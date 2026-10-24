#pragma once
// Confirm wiring/power/pH calibration first. -1 deliberately means unconfigured.
#define INOFARM_WIRING_ENABLED 0
#define INOFARM_I2C_SDA -1
#define INOFARM_I2C_SCL -1
#define INOFARM_LCD_ADDRESS 0
#define INOFARM_DS18B20_PIN -1
#define INOFARM_DHT_PIN -1
#define INOFARM_PH_PIN -1
#define INOFARM_SD_CS -1
#define INOFARM_SPI_SCK -1
#define INOFARM_SPI_MISO -1
#define INOFARM_SPI_MOSI -1
#define INOFARM_BUTTON_OK -1
#define INOFARM_BUTTON_BACK -1
#define INOFARM_BUTTON_RIGHT -1
#define INOFARM_BUTTON_LEFT -1
#define INOFARM_BUZZER -1
#define INOFARM_LED_GREEN -1
#define INOFARM_LED_RED -1
// Calibrated pH slope/intercept must come from bench measurements, never guessed.
#define INOFARM_PH_CALIBRATED 0
#define INOFARM_PH_SLOPE 0.0f
#define INOFARM_PH_INTERCEPT 0.0f
