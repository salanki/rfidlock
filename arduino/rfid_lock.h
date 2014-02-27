#include "Arduino.h"
#include "settings.h"

/* Lock */
#define LOCK_RELAY 8
#define BUZZER 5
#define RESET_PIN 6

/* RFID */
#define CODE_LEN 10      //Max length of RFID tag
#define RFID_ENABLE 2   //to RFID ENABLE

#define RESET_BOARD() digitalWrite(RESET_PIN, HIGH); //wdt_enable(WDTO_1S);  -- Sadly we can't use watchdog as it doesn't reset the WiFi shield

void clearCode();
void enableRFID();
void disableRFID();
boolean getRFIDTag();

extern char tag[CODE_LEN+1];
