#include <Arduino.h>
#include <ArduinoJson.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <SD.h>
#include <SPI.h>
#include <time.h>
#include <sys/time.h>
#include "core.h"
#include "transport.h"
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>
#include <new>
#if __has_include("secrets.h")
#include "secrets.h"
#else
#include "secrets.example.h"
#endif
#if __has_include("board_config.h")
#include "board_config.h"
#else
#include "board_config.example.h"
#endif
#if INOFARM_WIRING_ENABLED || defined(INOFARM_COMPILE_CHECK)
#define INOFARM_BUILD_DRIVERS 1
#else
#define INOFARM_BUILD_DRIVERS 0
#endif
#if INOFARM_BUILD_DRIVERS
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <DHT.h>
#include <RTClib.h>
#include <LiquidCrystal_I2C.h>
#if !defined(INOFARM_COMPILE_CHECK)
static_assert(INOFARM_I2C_SDA >= 0 && INOFARM_I2C_SCL >= 0 && INOFARM_LCD_ADDRESS > 0, "Confirm I2C wiring/address");
static_assert(INOFARM_DS18B20_PIN >= 0 && INOFARM_DHT_PIN >= 0 && INOFARM_PH_PIN >= 0, "Confirm sensor pins");
static_assert(INOFARM_SD_CS >= 0 && INOFARM_SPI_SCK >= 0 && INOFARM_SPI_MISO >= 0 && INOFARM_SPI_MOSI >= 0, "Confirm SD wiring");
static_assert(INOFARM_BUTTON_OK >= 0 && INOFARM_BUTTON_BACK >= 0 && INOFARM_BUTTON_RIGHT >= 0 && INOFARM_BUTTON_LEFT >= 0 && INOFARM_BUZZER >= 0 && INOFARM_LED_GREEN >= 0 && INOFARM_LED_RED >= 0, "Confirm buttons/indicators");
#endif
OneWire oneWire(INOFARM_DS18B20_PIN);
DallasTemperature waterSensor(&oneWire);
DHT roomSensor(INOFARM_DHT_PIN,DHT22);
RTC_DS3231 rtc;
LiquidCrystal_I2C lcd(INOFARM_LCD_ADDRESS,20,4);
bool rtcReady = false;
#endif

#ifndef INOFARM_ALLOW_HTTP_BENCH
#define INOFARM_ALLOW_HTTP_BENCH 0
#endif

using namespace inofarm;
Menu menu;
AlarmDeadline alarmDeadline;
String pendingManualEnvelope;
String pendingManualId;
struct NetworkWork { String operation,method,body,path,id; int code=-1;String response; };
QueueHandle_t networkRequests=nullptr,networkResults=nullptr;
bool networkBusy=false;
String lastQueuedPath;
String appliedBody;

bool sdReady = false;
DynamicJsonDocument configuration(12000);
DynamicJsonDocument liveSnapshot(1024);
String lastFeedback = "Belum ada hasil final";
const char* metrics[] = {"waterTemperatureC","solutionPh","airTemperatureC","relativeHumidityPercent"};
String phaseText() { return menu.draft.phase == Phase::Vegetatif ? "vegetatif":"generatif"; }
String eventUuid() {
    uint8_t bytes[16]; esp_fill_random(bytes,sizeof(bytes)); bytes[6]=(bytes[6]&0x0f)|0x40; bytes[8]=(bytes[8]&0x3f)|0x80;
    char out[37]; snprintf(out,sizeof(out),"%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",bytes[0],bytes[1],bytes[2],bytes[3],bytes[4],bytes[5],bytes[6],bytes[7],bytes[8],bytes[9],bytes[10],bytes[11],bytes[12],bytes[13],bytes[14],bytes[15]); return out;
}
String sourceTime() {
#if INOFARM_BUILD_DRIVERS
    if (!rtcReady || rtc.lostPower()) return "";
    // RTC is stored as UTC. Never adjust from compile time or server receive time.
    DateTime value = rtc.now(); char result[25]; snprintf(result,sizeof(result),"%04u-%02u-%02uT%02u:%02u:%02uZ",value.year(),value.month(),value.day(),value.hour(),value.minute(),value.second()); return result;
#else
    return "";
#endif
}
bool atomicWrite(const String& path, const String& body) {
    if (!sdReady) return false;
    String temporary=path+".tmp";
    File file=SD.open(temporary,FILE_WRITE);
    if (!file) return false;
    const size_t written=file.print(body); file.flush(); file.close();
    if (written != body.length()) return false;
    return SD.rename(temporary,path); // Existing events are never overwritten.
}
void readSensors(JsonObject snapshot, const String& timestamp) {
    if (timestamp.length()) snapshot["capturedAt"]=timestamp; else snapshot["capturedAt"]=nullptr;
    JsonObject readings=snapshot.createNestedObject("readings"); JsonObject quality=snapshot.createNestedObject("quality");
    for (const char* metric:metrics) { readings[metric]=nullptr; quality[metric]="missing"; }
#if INOFARM_BUILD_DRIVERS
    waterSensor.requestTemperatures(); float water=waterSensor.getTempCByIndex(0);
    if (water != DEVICE_DISCONNECTED_C && isfinite(water)) { readings[metrics[0]]=water; quality[metrics[0]]="ok"; } else quality[metrics[0]]="failed";
    float air=roomSensor.readTemperature(); float humidity=roomSensor.readHumidity();
    if (isfinite(air)) { readings[metrics[2]]=air; quality[metrics[2]]="ok"; } else quality[metrics[2]]="failed";
    if (isfinite(humidity)) { readings[metrics[3]]=humidity; quality[metrics[3]]="ok"; } else quality[metrics[3]]="failed";
#if INOFARM_PH_CALIBRATED
    float ph=analogReadMilliVolts(INOFARM_PH_PIN)*INOFARM_PH_SLOPE+INOFARM_PH_INTERCEPT;
    if (isfinite(ph)) { readings[metrics[1]]=ph; quality[metrics[1]]="ok"; } else quality[metrics[1]]="failed";
#else
    quality[metrics[1]]="uncalibrated";
#endif
#endif
}
bool persistEvent(const char* kind) {
    if (!strlen(INOFARM_DEVICE_ID)) { lastFeedback="Device belum provision"; return false; }
    if(strcmp(kind,"record")==0 && pendingManualEnvelope.length()) {
        bool saved=atomicWrite("/queue/"+pendingManualId+".json",pendingManualEnvelope);
        if(saved) { pendingManualEnvelope="";pendingManualId=""; }
        lastFeedback=saved?"Tersimpan SD; pending":"Gagal SD; payload tetap";
        return saved;
    }
    DynamicJsonDocument doc(4096); String id=eventUuid(); String timestamp=sourceTime();
    doc["schemaVersion"]="1"; doc["eventId"]=id; doc["deviceId"]=INOFARM_DEVICE_ID;
    doc["clockSource"]=timestamp.length()?"rtc":"unknown"; doc["clockStatus"]=timestamp.length()?"trusted":"invalid";
    if (strcmp(kind,"record")==0) {
        if (!menu.draft.measured) return false;
        if (timestamp.length()) doc["confirmedAt"]=timestamp; else doc["confirmedAt"]=nullptr;
        doc["measuredAt"]=nullptr; // Actual handheld measurement time is not known from navigation/confirmation.
        doc["phase"]=phaseText(); doc["manualPpm"]=menu.ppm();
    }
    if (liveSnapshot.isNull()) readSensors(liveSnapshot.to<JsonObject>(),timestamp);
    doc["sensorSnapshot"]=liveSnapshot.as<JsonObject>();
    DynamicJsonDocument envelope(4608); envelope["kind"]=kind; envelope["payload"]=doc.as<JsonObject>();
    String serialized; serializeJson(envelope,serialized);
    if(strcmp(kind,"record")==0) { pendingManualEnvelope=serialized;pendingManualId=id; }
    bool saved=atomicWrite("/queue/"+id+".json",serialized);
    if(saved && strcmp(kind,"record")==0) { pendingManualEnvelope="";pendingManualId=""; }
    lastFeedback=saved?"Tersimpan SD; pending":"Gagal SD; belum disimpan";
    return saved;
}

int api(const String& operation, const char* method, const String& body, String& response) {
    if (WiFi.status()!=WL_CONNECTED || !strlen(INOFARM_API_BASE) || !strlen(INOFARM_DEVICE_TOKEN)) return -1;
    if (!transportAllowed(INOFARM_API_BASE,INOFARM_ALLOW_HTTP_BENCH==1)) return -4;
    String url=String(INOFARM_API_BASE)+operation;
    HTTPClient http; WiFiClient plain; WiFiClientSecure secure;
    if (url.startsWith("https://")) {
        if (!strlen(INOFARM_ROOT_CA)) return -2;
        secure.setCACert(INOFARM_ROOT_CA);
        if (!http.begin(secure,url)) return -3;
    } else {
        // Host parsed before any request; HTTPS is the operational default.
        if (!http.begin(plain,url)) return -3;
    }
    http.setConnectTimeout(5000); http.setTimeout(10000); http.addHeader("Authorization",String("Bearer ")+INOFARM_DEVICE_TOKEN); http.addHeader("Accept","application/json"); http.addHeader("Content-Type","application/json");
    int code=http.sendRequest(method,body); response=http.getString(); http.end(); return code;
}
// The worker owns only HTTP objects and copied strings. SD, JSON state, buttons,
// reminder and LCD remain on the Arduino loop task; no shared mutable documents.
void networkTask(void*) {
    for(;;) {
        NetworkWork* work=nullptr;
        if(xQueueReceive(networkRequests,&work,portMAX_DELAY)==pdTRUE && work) {
            work->code=api(work->operation,work->method.c_str(),work->body,work->response);
            xQueueSend(networkResults,&work,portMAX_DELAY);
        }
    }
}
bool dispatchNetwork(const String& operation,const String& method,const String& body,const String& path="",const String& id="") {
    if(networkBusy || !networkRequests || WiFi.status()!=WL_CONNECTED) return false;
    NetworkWork* work=new(std::nothrow) NetworkWork;
    if(!work) { lastFeedback="Memori jaringan penuh";return false; }
    work->operation=operation;work->method=method;work->body=body;work->path=path;work->id=id;
    if(xQueueSend(networkRequests,&work,0)!=pdTRUE) { delete work;return false; }
    networkBusy=true;return true;
}
void applyConfiguration(const String& response) {
    DynamicJsonDocument incoming(12000); if(deserializeJson(incoming,response)) return;
    if(incoming["config"].isNull()) { configuration.clear();return; }
    String path="/config-"+incoming["config"]["id"].as<String>()+".json";
    if(!sdReady) { lastFeedback="Config belum diterapkan";return; }
    if(!SD.exists(path) && !atomicWrite(path,response)) return;
    configuration.set(incoming);
    appliedBody="{\"version\":"+incoming["config"]["id"].as<String>()+"}";
}
void consumeNetwork() {
    NetworkWork* work=nullptr;
    if(!networkResults || xQueueReceive(networkResults,&work,0)!=pdTRUE) return;
    networkBusy=false;
    if(work->operation=="device/config") {
        if(work->code==200) applyConfiguration(work->response);
    } else if(work->path.length()) {
        DynamicJsonDocument ack(16000);bool parsed=!deserializeJson(ack,work->response);
        String returned=ack["event"]["event_id"] | "";
        const Delivery decision=deliveryDecision(work->code,parsed,ack["persisted"] | false,work->id.c_str(),returned.c_str());
        if(decision==Delivery::PersistAck) {
            if(atomicWrite(work->path+".ack",work->response)) {
                String status=ack["event"]["validation_result"]["status"] | "";
                lastFeedback=status=="valid"?"Catatan valid":status=="issues"?"Catatan bermasalah":status=="incomplete"?"Belum dapat validasi":"Monitoring tersimpan";
#if INOFARM_BUILD_DRIVERS
                digitalWrite(INOFARM_LED_GREEN,status=="valid");digitalWrite(INOFARM_LED_RED,status=="issues");
#endif
            } else lastFeedback="ACK belum durable";
        } else if(decision==Delivery::PreserveBlocked) {
            atomicWrite(work->path+".blocked",String(work->code)+"; payload retained for review");
            lastFeedback="Event ditahan; cek owner";
        } else lastFeedback="Pending; koneksi/API";
    } else if(work->operation=="device/config-applied" && work->code==200) {
        DynamicJsonDocument ack(128);
        if(!deserializeJson(ack,work->response) && ack["appliedVersion"].as<String>()==configuration["config"]["id"].as<String>()) appliedBody="";
    }
    delete work;
}
void replayOne() {
    if(!sdReady || networkBusy || WiFi.status()!=WL_CONNECTED) return;
    File directory=SD.open("/queue");if(!directory) return;
    File file=directory.openNextFile();String selected="";
    while(file) {
        String name=file.name();if(!name.startsWith("/")) name="/queue/"+name;
        if(name.endsWith(".json") && !SD.exists(name+".ack") && !SD.exists(name+".blocked") && precedesCandidate(name.c_str(),selected.c_str(),lastQueuedPath.c_str())) selected=name;
        file.close();file=directory.openNextFile();
    }
    directory.close();if(!selected.length()) return;
    lastQueuedPath=selected;
    File payloadFile=SD.open(selected);DynamicJsonDocument envelope(4608);
    bool parsed=payloadFile && !deserializeJson(envelope,payloadFile);payloadFile.close();
    String kind=envelope["kind"] | "";String id=envelope["payload"]["eventId"] | "";
    if(!parsed || (kind!="record" && kind!="telemetry") || !id.length() || !envelope["payload"].is<JsonObject>()) {
        atomicWrite(selected+".blocked","Malformed envelope retained for review");lastFeedback="Queue rusak; dipertahankan";return;
    }
    String payload;serializeJson(envelope["payload"],payload);
    dispatchNetwork("device/events/"+kind,"POST",payload,selected,id);
}
void loadCachedConfiguration() {
    if (!sdReady) return;
    File directory=SD.open("/"); File file=directory.openNextFile(); uint64_t latest=0;
    while(file) {
        String name=file.name();
        if(name.indexOf("config-")>=0 && name.endsWith(".json")) {
            DynamicJsonDocument cached(12000);
            if(!deserializeJson(cached,file)) {
                uint64_t version=cached["config"]["id"] | 0ULL;
                if(version>latest) { configuration.set(cached); latest=version; }
            }
        }
        file.close(); file=directory.openNextFile();
    }
    directory.close();
    if(latest) lastFeedback="Config cache; offline";
}
String readingText(const char* metric) {
    return liveSnapshot["readings"][metric].isNull() ? "--" : String(liveSnapshot["readings"][metric].as<float>(),1);
}
void checkReminder() {
#if INOFARM_BUILD_DRIVERS
    static String lastOccurrence="";
    if(alarmDeadline.expired(millis())) digitalWrite(INOFARM_BUZZER,LOW);
    unsigned duration=configuration["config"]["body"]["alarmSeconds"] | 0u;
    if(!duration || !rtcReady || rtc.lostPower()) return;
    DateTime local(rtc.now().unixtime()+7*3600); int day=local.dayOfTheWeek(); if(day==0) day=7;
    char localTime[6]; snprintf(localTime,sizeof(localTime),"%02u:%02u",local.hour(),local.minute());
    for(JsonObject schedule:configuration["config"]["body"]["schedules"].as<JsonArray>()) {
        if(schedule["time"].as<String>()!=localTime) continue;
        bool scheduled=false; for(int activeDay:schedule["days"].as<JsonArray>()) if(activeDay==day) scheduled=true;
        if(!scheduled) continue;
        String occurrence=String(local.year())+"-"+String(local.month())+"-"+String(local.day())+"-"+String(local.hour())+"-"+String(local.minute());
        if(lastOccurrence!=occurrence) {
            lastOccurrence=occurrence; String path="/alarm-"+occurrence+".ack";
            if(sdReady && SD.exists(path)) return;
            if(sdReady && !atomicWrite(path,"reminder started")) lastFeedback="Pengingat; SD gagal"; else lastFeedback="Waktunya nutrisi manual";
            digitalWrite(INOFARM_BUZZER,HIGH); alarmDeadline.start(millis(),duration*1000u);
        }
    }
#endif
}
void showLcd() {
#if INOFARM_BUILD_DRIVERS
    String lines[4];
    if (menu.screen==Screen::Monitoring) {
        lines[0]="Air "+readingText(metrics[0])+" pH "+readingText(metrics[1]);
        lines[1]="Ruang "+readingText(metrics[2])+" H "+readingText(metrics[3]);
        if (rtcReady && !rtc.lostPower()) {
            DateTime local(rtc.now().unixtime()+7*3600);
            char clock[13]; snprintf(clock,sizeof(clock),"%02u:%02u:%02u WIB",local.hour(),local.minute(),local.second());
            lines[2]=clock;
        } else lines[2]="Waktu belum dipercaya";
        lines[3]=lastFeedback;
    } else if (menu.screen==Screen::Phase) { lines[0]="Pilih fase catatan"; lines[1]=phaseText(); lines[2]="LEFT/RIGHT ubah"; lines[3]="OK lanjut BACK batal"; }
    else if (menu.screen==Screen::Ppm) { lines[0]="PPM handheld manual"; lines[1]=menu.draft.digits.c_str(); lines[2]="Digit "+String(menu.cursor+1)+" LEFT/RIGHT"; lines[3]="OK next; BACK fase"; }
    else if (menu.screen==Screen::Review) { lines[0]=phaseText()+" "+String(menu.ppm())+" PPM"; lines[1]="Air "+readingText(metrics[0])+" pH "+readingText(metrics[1]); lines[2]="Ruang "+readingText(metrics[2])+" H "+readingText(metrics[3]); lines[3]="OK simpan BACK ubah"; }
    else { lines[0]=menu.screen==Screen::Error?"Gagal simpan lokal":"Tersimpan lokal"; lines[1]=lastFeedback; lines[2]="Final dari backend"; lines[3]="BACK kembali"; }
    for (int row=0;row<4;row++) { lcd.setCursor(0,row); String line=lines[row].substring(0,20); while(line.length()<20) line+=' '; lcd.print(line); }
#endif
}
void handleButton(Button button) {
    alarmDeadline.stop();
    if(menu.screen==Screen::Review && button==Button::Back) { pendingManualEnvelope="";pendingManualId=""; }
#if INOFARM_BUILD_DRIVERS
    digitalWrite(INOFARM_BUZZER,LOW); // Physical acknowledgement; never creates a nutrient record by itself.
#endif
    if (menu.screen==Screen::Review && button==Button::Ok) menu.stored(persistEvent("record")); else menu.press(button);
    showLcd();
}
void setup() {
    Serial.begin(115200);
#if INOFARM_BUILD_DRIVERS
    Wire.begin(INOFARM_I2C_SDA,INOFARM_I2C_SCL); rtcReady=rtc.begin(); lcd.init(); lcd.backlight(); waterSensor.begin(); roomSensor.begin();
    if (rtcReady && !rtc.lostPower()) {
        // Certificate verification needs wall-clock UTC from the configured RTC.
        timeval systemClock={static_cast<time_t>(rtc.now().unixtime()),0};
        settimeofday(&systemClock,nullptr);
    }
    SPI.begin(INOFARM_SPI_SCK,INOFARM_SPI_MISO,INOFARM_SPI_MOSI,INOFARM_SD_CS); sdReady=SD.begin(INOFARM_SD_CS);
    for (int pin:{INOFARM_BUTTON_OK,INOFARM_BUTTON_BACK,INOFARM_BUTTON_RIGHT,INOFARM_BUTTON_LEFT}) pinMode(pin,INPUT_PULLUP);
    for (int pin:{INOFARM_BUZZER,INOFARM_LED_GREEN,INOFARM_LED_RED}) { pinMode(pin,OUTPUT); digitalWrite(pin,LOW); }
    if (sdReady) SD.mkdir("/queue");
#endif
    // No simulated sensors, clock, roster, thresholds or agronomic production defaults.
    if (strlen(INOFARM_WIFI_SSID)) WiFi.begin(INOFARM_WIFI_SSID,INOFARM_WIFI_PASSWORD);
    networkRequests=xQueueCreate(1,sizeof(NetworkWork*));networkResults=xQueueCreate(1,sizeof(NetworkWork*));
    if(networkRequests && networkResults && xTaskCreate(networkTask,"inofarm-http",8192,nullptr,1,nullptr)!=pdPASS) { networkRequests=nullptr;lastFeedback="Jaringan gagal mulai"; }
    loadCachedConfiguration();
    readSensors(liveSnapshot.to<JsonObject>(),sourceTime());
    Serial.println("Inofarm core ready. Physical wiring not enabled unless configured.");
    showLcd();
}
void loop() {
    static uint32_t previousNetwork=0,previousDisplay=0,previousTelemetry=0,previousSensor=0,previousReplay=0,previousApplied=0;
    if (Serial.available()) { char key=Serial.read(); if (key=='o') handleButton(Button::Ok); if (key=='b') handleButton(Button::Back); if (key=='r') handleButton(Button::Right); if (key=='l') handleButton(Button::Left); }
#if INOFARM_BUILD_DRIVERS
    static bool previous[4]={false,false,false,false}; static uint32_t lastPress[4]={0,0,0,0};
    int pins[]={INOFARM_BUTTON_OK,INOFARM_BUTTON_BACK,INOFARM_BUTTON_RIGHT,INOFARM_BUTTON_LEFT}; Button keys[]={Button::Ok,Button::Back,Button::Right,Button::Left};
    for (int i=0;i<4;i++) { bool pressed=digitalRead(pins[i])==LOW; if (pressed && !previous[i] && millis()-lastPress[i]>50) { handleButton(keys[i]); lastPress[i]=millis(); } previous[i]=pressed; }
#endif
    consumeNetwork();
    checkReminder();
    if(!networkBusy) {
        if(millis()-previousNetwork>=30000) { if(dispatchNetwork("device/config","GET","")) previousNetwork=millis(); }
        else if(appliedBody.length() && millis()-previousApplied>=30000) { if(dispatchNetwork("device/config-applied","POST",appliedBody)) previousApplied=millis(); }
        else if(millis()-previousReplay>=100) { previousReplay=millis();replayOne(); }
    }
    if (millis()-previousSensor>=2000) { previousSensor=millis(); liveSnapshot.clear(); readSensors(liveSnapshot.to<JsonObject>(),sourceTime()); }
    unsigned cadence=configuration["config"]["body"]["telemetryIntervalSeconds"] | 0u;
    if (cadence && millis()-previousTelemetry>=cadence*1000u) { previousTelemetry=millis(); persistEvent("telemetry"); }
    if (millis()-previousDisplay>1000) { previousDisplay=millis(); showLcd(); }
    checkReminder();
    delay(10);
}
