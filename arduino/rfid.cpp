#include "Arduino.h"
#include "rfid_lock.h"

#define VALIDATE_TAG 1  //should we validate tag?
#define VALIDATE_LENGTH  200 //maximum reads b/w tag read and validate
#define START_BYTE 0x0A 
#define STOP_BYTE 0x0D

boolean isCodeValid();

void enableRFID() {
   digitalWrite(RFID_ENABLE, LOW);    
}
 
void disableRFID() {
   digitalWrite(RFID_ENABLE, HIGH);  
}
 
/**
 * Blocking function, waits for and gets the RFID tag.
 */
boolean getRFIDTag() {
  byte next_byte;
  
  if(Serial.available() <= 0) return false;
  
  if((next_byte = Serial.read()) == START_BYTE) {      
    byte bytesread = 0; 
    while(bytesread < CODE_LEN) {
      if(Serial.available() > 0) { //wait for the next byte
          if((next_byte = Serial.read()) == STOP_BYTE) break;
          tag[bytesread++] = next_byte;
          tag[bytesread+1] = '\0';
      }
    }
    boolean codeValid = isCodeValid();
    if(isCodeValid()) return true;
    else {
      clearCode();
      return false;
    }
  } else return false;    
}
 
/**
 * Waits for the next incoming tag to see if it matches
 * the current tag.
 */
boolean isCodeValid() {
  byte next_byte; 
  int count = 0;
  while (Serial.available() < 2) {  //there is already a STOP_BYTE in buffer
    delay(1); //probably not a very pure millisecond
    if(count++ > VALIDATE_LENGTH) return false;
  }
  Serial.read(); //throw away extra STOP_BYTE
  if ((next_byte = Serial.read()) == START_BYTE) {  
    byte bytes_read = 0; 
    while (bytes_read < CODE_LEN) {
      if (Serial.available() > 0) { //wait for the next byte      
          if ((next_byte = Serial.read()) == STOP_BYTE) break;
          if (tag[bytes_read++] != next_byte) return false;                     
      }
    }
    return true;    
  }
  return false;   
}

/**
 * Clears out the memory space for the tag to 0s.
 */
void clearCode() {
  for(int i=0; i<CODE_LEN; i++) {
    tag[i] = 0; 
  }
}
