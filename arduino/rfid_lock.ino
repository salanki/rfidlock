
#include <avr/wdt.h>
#include "rfid_lock.h"

short listRevision = 0;
unsigned long lastTime, nextCheck;
boolean forceOpen = false;

char tagList[MAX_TAGS][CODE_LEN] = {"A","B","C", ""};

/* RFID */
#define ITERATION_LENGTH 2000 //time, in ms, given to the user to move hand away
char tag[CODE_LEN+1] = "";

void buzzSingle() {
  tone(BUZZER, 900, 500);
}

void buzzFail() {
  tone(BUZZER, 300, 500);
}

void earlyOpenLock() {
#ifdef DEBUG
 Serial.println(F("Lock early open"));
#endif
  digitalWrite(LOCK_RELAY, HIGH);
}

void openLock(unsigned int time = LOCK_OPENS*1000) {
 forceOpen = false;
#ifdef DEBUG
 Serial.println(F("Lock open"));
#endif
  digitalWrite(LOCK_RELAY, HIGH);
  delay(time);
  digitalWrite(LOCK_RELAY, LOW);
  Serial.println(F("Lock closed"));
  wifiSetup(); /* hack due to voltage dip that kills the WiFiShield when lock is closed */
  //RESET_BOARD(); /* Super hack due to voltage dip that kills the WiFiShield when lock is closed */
}

void setup() {
  Serial.begin(2400);
  Serial.println(F("Starting up..."));

  pinMode(RFID_ENABLE, OUTPUT);
  pinMode(LOCK_RELAY, OUTPUT);
  pinMode(4, OUTPUT); // SD Card
  pinMode(RESET_PIN, OUTPUT);
  digitalWrite(RESET_PIN, LOW); /* This is such a horrible hack it makes me want to cry, but I can't find any way to reset the wifi shield from software and it is just too crappy to stay up for longer periods without reset */
  digitalWrite(LOCK_RELAY, LOW);
  digitalWrite(4, HIGH);
  
  lastTime = millis();

  disableRFID();
  clearCode();

  if(!commSetup()) {
    delay(30000); /* Wait 30s to not overload the AP with associations */
    RESET_BOARD();
  }
 
  nextCheck = millis()+CHECK_INTERVAL;

  Serial.println();
 // buzzSingle();
}

void loop() {
  short res;
  enableRFID();

  if(getRFIDTag()) {
    disableRFID();
    buzzSingle();
#ifdef DEBUG
    sendCode();
#endif
    
    if((res = authorize_tag(tag)) > 0) {
      unsigned long start = millis();
      
      earlyOpenLock();
      
      Serial.print(F("Permitting: "));
      Serial.println(tag);
      if(res == 2) log_access(tag);
#ifdef DEBUG
      Serial.print(F("Logging took: "));
      Serial.println(millis()-start);
#endif
      int diff = LOCK_OPENS*1000-(millis()-start);
#ifdef DEBUG
      Serial.print(F("Remaining open: "));
      Serial.println(diff);
#endif
      if(diff < 0 || diff > 120000) openLock(0); /* An upper boundry check is added for the small possibility of timer wrap */
      else openLock(diff);
      
    } else {
      buzzFail();
      Serial.print(F("Denying: "));
      Serial.println(tag);
    }
    
    clearCode();
  }
  
  httpListener();
  if(forceOpen) openLock();
  
  /* Timers */
  if(lastTime > millis()) { /* Timer wrap */
    nextCheck = 0;
    lastTime = millis();
  }
  if(millis() > nextCheck) {
    disableRFID();
#ifdef DEBUG
    Serial.println(F("Routine server check"));
#endif
    checkServer();
    nextCheck = millis()+CHECK_INTERVAL;
#ifdef DEBUG
    Serial.print("Nextcheck: ");
    Serial.println(nextCheck);
#endif
  } 
}

short authorize_tag(char *tag) {
 /* Try to authorize from our local tag list */
 for(int i = 0; tagList[i][0] != '\0'; ++i) {
   if(strncmp(tagList[i], tag, CODE_LEN) == 0) {
     /* Found in local tag list */
#ifdef DEBUG
     Serial.println("Local hit");
#endif
     return 2;
   }
 }
 if(server_authorize_tag(tag)) return 1;
 else return 0;
}

/**
 * Sends the tag to the computer.
 */
void sendCode() {
  Serial.print(F("Read tag: "));  
  //Serial.println(tag);
  for(int i=0; i<CODE_LEN; i++) {
    Serial.print(tag[i]); 
  }
  Serial.println();
}

