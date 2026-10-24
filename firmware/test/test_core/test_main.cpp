#include <unity.h>
#include "core.h"
#include "transport.h"
using namespace inofarm;
void setUp(void) {}
void tearDown(void) {}

void menu_requires_review_and_durable_ack() {
    Menu menu;
    menu.press(Button::Ok); TEST_ASSERT_EQUAL(int(Screen::Phase),int(menu.screen));
    menu.press(Button::Right); TEST_ASSERT_EQUAL(int(Phase::Generatif),int(menu.draft.phase));
    menu.press(Button::Ok); menu.press(Button::Right);
    for (int i=0;i<6;i++) menu.press(Button::Ok);
    TEST_ASSERT_EQUAL(int(Screen::Review),int(menu.screen));
    menu.press(Button::Ok); TEST_ASSERT_EQUAL(int(Screen::Review),int(menu.screen));
    menu.stored(false); TEST_ASSERT_EQUAL(int(Screen::Error),int(menu.screen));
    menu.press(Button::Back); TEST_ASSERT_EQUAL(int(Screen::Review),int(menu.screen));
    menu.stored(true); TEST_ASSERT_EQUAL(int(Screen::Pending),int(menu.screen));
}
void cancel_does_not_create_record() {
    Menu menu; menu.press(Button::Ok); menu.press(Button::Back);
    TEST_ASSERT_EQUAL(int(Screen::Monitoring),int(menu.screen)); TEST_ASSERT_FALSE(menu.draft.measured);
}
void acknowledgement_only_after_persisted_same_id() {
    TEST_ASSERT_FALSE(acknowledgementMatches("abc",false,"abc"));
    TEST_ASSERT_FALSE(acknowledgementMatches("abc",true,"other"));
    TEST_ASSERT_TRUE(acknowledgementMatches("abc",true,"abc"));
}
void half_open_and_sunday_rollover() {
    TEST_ASSERT_TRUE(contains(0,0,6,1200,240));
    TEST_ASSERT_FALSE(contains(0,240,6,1200,240));
    TEST_ASSERT_TRUE(contains(0,240,0,240,600));
}
void transport_gate_parses_host_instead_of_prefix() {
    TEST_ASSERT_FALSE(transportAllowed("http://192.168.1.2/api/v1/",false));
    TEST_ASSERT_TRUE(transportAllowed("http://192.168.1.2:8001/api/v1/",true));
    TEST_ASSERT_TRUE(transportAllowed("http://172.31.255.254/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("http://172.32.1.2/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("http://192.168.evil.invalid/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("http://10.1.2.3@evil.invalid/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("http://127.0.0.1.evil.invalid/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("http://10.999.1.2/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("http://010.1.2.3/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("http://10.1.2.3:65536/api/v1/",true));
    TEST_ASSERT_FALSE(transportAllowed("https://server.invalid/api/v1/?redirect=evil/",false));
    TEST_ASSERT_TRUE(transportAllowed("https://server.invalid/api/v1/",false));
}
void queue_errors_and_mismatched_ack_keep_payload() {
    TEST_ASSERT_EQUAL(int(Delivery::Retry),int(deliveryDecision(201,true,true,"stable-id","different")));
    TEST_ASSERT_EQUAL(int(Delivery::Retry),int(deliveryDecision(201,false,true,"stable-id","stable-id")));
    TEST_ASSERT_EQUAL(int(Delivery::PreserveBlocked),int(deliveryDecision(409,true,false,"stable-id","")));
    TEST_ASSERT_EQUAL(int(Delivery::PreserveBlocked),int(deliveryDecision(422,true,false,"stable-id","")));
    TEST_ASSERT_EQUAL(int(Delivery::Retry),int(deliveryDecision(429,true,false,"stable-id","")));
    TEST_ASSERT_EQUAL(int(Delivery::Retry),int(deliveryDecision(503,true,false,"stable-id","")));
    TEST_ASSERT_EQUAL(int(Delivery::PersistAck),int(deliveryDecision(200,true,true,"stable-id","stable-id")));
}
void backlog_progresses_past_conflict_and_timeout_without_deletion() {
    struct Entry { std::string path,payload;bool blocked,acked; Entry(std::string p,std::string b):path(p),payload(b),blocked(false),acked(false) {} };
    std::vector<Entry> entries={{"a","corrupt"},{"b","stable-id-b/body"},{"c","stable-id-c/body"},{"d","stable-id-d/body"}};
    std::string after="";unsigned completed=0;
    for(unsigned cycle=0;cycle<8;cycle++) {
        std::string selected="";int index=-1;
        for(unsigned i=0;i<entries.size();i++) if(!entries[i].blocked && !entries[i].acked && precedesCandidate(entries[i].path,selected,after)) { selected=entries[i].path;index=int(i); }
        if(index<0) break;
        auto& event=entries[index];after=selected;
        if(index==0) { event.blocked=true;continue; }
        int code=index==1?409:index==2 && cycle<4?503:201;
        Delivery result=deliveryDecision(code,true,code==201,event.payload,event.payload);
        if(result==Delivery::PreserveBlocked) event.blocked=true;
        if(result==Delivery::PersistAck) { // First simulated ACK write fails; next replay remains byte-identical.
            if(index==3 && cycle<4) continue;
            event.acked=true;++completed;
        }
    }
    TEST_ASSERT_EQUAL(4,entries.size());TEST_ASSERT_TRUE(entries[0].blocked);TEST_ASSERT_TRUE(entries[1].blocked);
    TEST_ASSERT_EQUAL(2,completed);TEST_ASSERT_EQUAL_STRING("stable-id-c/body",entries[2].payload.c_str());
}
void alarm_deadline_works_during_network_delay_and_wraparound() {
    AlarmDeadline alarm;alarm.start(100,200);
    // Networking remains in flight; the coordinator still checks the deadline and buttons.
    TEST_ASSERT_FALSE(alarm.expired(299));TEST_ASSERT_TRUE(alarm.expired(300));
    alarm.start(0xfffffff0u,32);TEST_ASSERT_FALSE(alarm.expired(0));TEST_ASSERT_TRUE(alarm.expired(16));
    alarm.start(100,500);alarm.stop();TEST_ASSERT_FALSE(alarm.expired(900));
}
int main() { UNITY_BEGIN(); RUN_TEST(menu_requires_review_and_durable_ack); RUN_TEST(cancel_does_not_create_record); RUN_TEST(acknowledgement_only_after_persisted_same_id); RUN_TEST(half_open_and_sunday_rollover); RUN_TEST(transport_gate_parses_host_instead_of_prefix); RUN_TEST(queue_errors_and_mismatched_ack_keep_payload); RUN_TEST(backlog_progresses_past_conflict_and_timeout_without_deletion); RUN_TEST(alarm_deadline_works_during_network_delay_and_wraparound); return UNITY_END(); }
