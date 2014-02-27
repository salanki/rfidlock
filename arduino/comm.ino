#include <WiFi.h>
#include <HTTPClient.h>
#include <stdio.h>
#include <avr/wdt.h>


#define VERIFY_URI "/lock/verify"
#define LIST_URI "/tags/lockList" 
#define AUTHORIZE_URI "/enter/authorize"
#define LOG_ACCESS_URI "/enter/log"

#define LISTENER_REFRESH "GET /r " // add space
#define LISTENER_OPEN "GET /o/" COM_OPENSECRET " "
#define LISTENER_RCVBUF_LEN sizeof(LISTENER_OPEN)+1

void send_http_ok_header(WiFiClient target);

HTTPClient client(COM_HOSTNAME, COM_PORT);
WiFiServer listener(COM_LISTENPORT);
short failed_gets = 0;

struct http_result {
  int status;
  FILE *data;
};

boolean wifiSetup() {
  int status = WL_IDLE_STATUS;     // the Wifi radio's status

  status = WiFi.begin(COM_SSID_NAME, COM_PASSPHRASE);
  if(status != WL_CONNECTED) {
    Serial.println(F("Wifi Association failed."));
    return false;
  }
  return true;
}

boolean commSetup() {
  wifiSetup();

  printCurrentNet();
  printWifiData();

  listener.begin();

  while(!checkServer()) delay(50);

  Serial.println(F("Comm setup successful"));

  return true;
}

boolean checkServer() {
  char buffer[sizeof(VERIFY_URI)+sizeof(COM_SECRET)+9];

  sprintf_P(buffer, PSTR("%s/%i?s=%s"), VERIFY_URI, listRevision, COM_SECRET);

  struct http_result result = httpGet(buffer);
  if(result.status == -1) return false;
  else if(result.status == 200) {
    client.closeStream(result.data);
    return true;
  }
  else if(result.status == 401) {
    Serial.println(F("Shared secret does not match with server"));
    client.closeStream(result.data);
    return false;
  }
  else if(result.status == 206) { /* Partial Content, our tag list is out of date and needs updating */
    client.closeStream(result.data);
    return getList();
  } 
  else if(result.status == 205) { /* Reset Content, we need to restart ourselves */
    client.closeStream(result.data);
    RESET_BOARD();

    Serial.println(F("Resetting"));
    return true;
  } 
  else {
    Serial.print(F("Unknown server status: "));
    Serial.println(result.status);

    client.closeStream(result.data);
    return false;
  }
}

/*
 * Format from server must be:
 * <revision>
 * <key1>
 * ...
 * <keyN>
 * newLine (\n)
 * anything
 */
boolean getList() {
  char buffer[sizeof(LIST_URI)+sizeof(COM_SECRET)+2];
  sprintf_P(buffer, PSTR("%s?s=%s"), LIST_URI, COM_SECRET);

  struct http_result result = httpGet(buffer);
  if(result.status == -1) return false;
  else if(result.status == 200) {
    /* Parse keys */
    char rev[7];
    char buffer[CODE_LEN+3];
    int i;

    fgets(rev, 7, result.data);
    listRevision = atoi(rev);

    for(i = 0; i < MAX_TAGS-1; ++i) {
      fgets(buffer, CODE_LEN+2, result.data);
      if(buffer[0] == '\n') break;
      for(int k = 0; k < CODE_LEN; ++k) tagList[i][k] = buffer[k];

    }
    tagList[i][0] = '\0';

    Serial.print(F("Loaded: "));
    Serial.print(i);
    Serial.print(F(" keys, rev: "));
    Serial.println(listRevision);

    client.closeStream(result.data);
    return true;
  }
  else if(result.status == 401) {
    Serial.println(F("Shared key does not match with server"));
    client.closeStream(result.data);
    return false;
  }
  else {
    Serial.print(F("GL Unknown server status: "));
    Serial.println(result.status);

    client.closeStream(result.data);
    return false;
  }
}

boolean server_authorize_tag(char *tag) {
  char buffer[sizeof(AUTHORIZE_URI)+CODE_LEN+1];
  sprintf_P(buffer, PSTR("%s/%s"), AUTHORIZE_URI, tag);

  struct http_result result = httpGet(buffer);
  if(result.status == -1) return false;
  else if(result.status == 200) {
    client.closeStream(result.data);
    return true;
  }
  else if(result.status == 404) {
    client.closeStream(result.data);
    return false;
  }
  else {
    Serial.print(F("AT Unknown server status: "));
    Serial.println(result.status);

    client.closeStream(result.data);
    return false;
  }
}

boolean log_access(char *tag) {
  char buffer[sizeof(LOG_ACCESS_URI)+CODE_LEN+1];
  sprintf_P(buffer, PSTR("%s/%s"), LOG_ACCESS_URI, tag);

  struct http_result result = httpGet(buffer);
  if(result.status == -1) return false;
  else if(result.status == 200) {
    client.closeStream(result.data);
#ifdef DEBUG
    Serial.println("Access logged");
#endif
    return true;
  }
  else {
    Serial.print(F("LA Unknown server status: "));
    Serial.println(result.status);

    client.closeStream(result.data);
    return false;
  }
}

struct http_result httpGet(char *uri) {
  struct http_result result;

#ifdef LINKDEBUG
  client.debug(-1);
#endif
#ifdef COM_WDT
  wdt_enable(WDTO_8S); /* If a request for some reason does not return within 8 seconds we will reset, this is not optimal but the Arduino TCP library doesn't handle timeouts very well */
#endif
  result.data = client.getURI(uri);
  result.status = client.getLastReturnCode();
#ifdef COM_WDT
  wdt_disable();
#endif

  if (result.data == NULL) {
    if(failed_gets++ > COM_MAX_FAILED_GETS) {
      Serial.println(F("Too many failed gets, resetting board"));
      Serial.flush();
      delay(200); /* Delay a bit to make sure that the message is sent out over serial */

      RESET_BOARD();
    }
    Serial.println(F("HTTP failed to connect"));
    result.status = -1;
  } 
  else failed_gets = 0;
  return result;
}

void httpListener() {
  WiFiClient listen_client = listener.available();
  if (listen_client) {
#ifdef DEBUG
    Serial.println("new client");
#endif
    // an http request ends with a blank line
    boolean currentLineIsBlank = true;
    char rcvbuf[LISTENER_RCVBUF_LEN] = "";
    unsigned short rcvdBytes = 0;

    while (listen_client.connected()) {
      if (listen_client.available()) {
        char c = listen_client.read();
        if(c == '\0') continue;

        /* Read in as much data as we need to determine if the request is valid */
        if(rcvdBytes < LISTENER_RCVBUF_LEN-1) {
          rcvbuf[rcvdBytes] = c;
          rcvbuf[++rcvdBytes] = '\0';
        }

        // if you've gotten to the end of the line (received a newline
        // character) and the line is blank, the http request has ended,
        // so you can send a reply

        if (c == '\n' && currentLineIsBlank) {          
          /* Handle what was requested */
          if(strncmp_P(rcvbuf, PSTR(LISTENER_REFRESH), sizeof(LISTENER_REFRESH)-1) == 0) {
            nextCheck = millis()+1000;

            send_http_ok_header(listen_client);
            listen_client.println(F("OK"));
          } 
          else if(strncmp_P(rcvbuf, PSTR(LISTENER_OPEN), sizeof(LISTENER_OPEN)-1) == 0) {
            forceOpen = true;
            Serial.println(F("Opening lock by server request"));

            send_http_ok_header(listen_client);
            listen_client.println(F("OK"));
          } 
          else {
            listen_client.println(F("HTTP/1.1 400 BAD REQUEST"));
            listen_client.println(F("Connection: close"));  // the connection will be closed after completion of the response
            listen_client.println();
          }
          break;
        }
        if (c == '\n') {
          // you're starting a new lineok
          currentLineIsBlank = true;
        } 
        else if (c != '\r' && c != '\0') {
          // you've gotten a character on the current line
          currentLineIsBlank = false;
        }
      }
    }
    // give the web browser time to receive the data
    delay(1);

    // close the connection:
    listen_client.stop();
#ifdef DEBUG
    Serial.println("client disonnected");
#endif
  }
}

void send_http_ok_header(WiFiClient target) {
  target.println(F("HTTP/1.1 200 OK"));
  target.println(F("Content-Type: text/plain"));
  target.println(F("Connection: close"));  // the connection will be closed after completion of the response
  target.println();
}

/* Support methods */
void printWifiData() {
  // print your WiFi shield's IP address:
  IPAddress ip = WiFi.localIP();
  Serial.print(F("IP Address: "));
  Serial.println(ip);

  // print your MAC address:
  byte mac[6];  
  WiFi.macAddress(mac);
  Serial.print(F("MAC address: "));
  Serial.print(mac[5],HEX);
  Serial.print(F(":"));
  Serial.print(mac[4],HEX);
  Serial.print(F(":"));
  Serial.print(mac[3],HEX);
  Serial.print(F(":"));
  Serial.print(mac[2],HEX);
  Serial.print(F(":"));
  Serial.print(mac[1],HEX);
  Serial.print(F(":"));
  Serial.println(mac[0],HEX);

}

void printCurrentNet() {
  // print the SSID of the network you're attached to:
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());

  // print the MAC address of the router you're attached to:
  byte bssid[6];
  WiFi.BSSID(bssid);    
  Serial.print(F("BSSID: "));
  Serial.print(bssid[5],HEX);
  Serial.print(F(":"));
  Serial.print(bssid[4],HEX);
  Serial.print(F(":"));
  Serial.print(bssid[3],HEX);
  Serial.print(F(":"));
  Serial.print(bssid[2],HEX);
  Serial.print(F(":"));
  Serial.print(bssid[1],HEX);
  Serial.print(F(":"));
  Serial.println(bssid[0],HEX);

  // print the received signal strength:
  long rssi = WiFi.RSSI();
  Serial.print(F("signal strength (RSSI):"));
  Serial.println(rssi);

  // print the encryption type:
  byte encryption = WiFi.encryptionType();
  Serial.print(F("Encryption Type:"));
  Serial.println(encryption,HEX);
  Serial.println();
} 




