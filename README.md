# RFID-Lock for Arduino

An Arduino based automatic door lock. Lock can be opened by holding an RFID-tag in front of an RFID-reader or by pressing the "Open Lock" button in the web-base management interface. The Arduino keeps an up to date local copy of authorized tags, and also logs all entry attempts to the management server. It uses the [Arduino Wifi Shield](https://www.sparkfun.com/products/11287) to communicate with the server, which can be run on a free [Heroku](https://www.heroku.com/) instance.


## Required Hardware
* Any Arduino board. I use the [Uno](http://arduino.cc/en/Main/ArduinoBoardUno#.Uw-68PSwL9k)
* The [Arduino Wifi Shield](https://www.sparkfun.com/products/11287) for wireless communication with the server. An [Ethernet Shield](http://arduino.cc/en/Main/ArduinoEthernetShield#.Uw-7hPSwL9k) should be usable with minor modifications
* A [Parallax RFID Card Reader](http://www.parallax.com/StoreSearchResults/tabid/768/txtSearch/28140/List/0/SortField/4/ProductID/114/Default.aspx)
* An [Electric Strike](http://www.ebay.com/itm/Door-Fail-Secure-access-control-Electric-Strike-v6-NO-/160364888288?pt=LH_DefaultDomain_0&hash=item25567e00e0) to open and close the physical lock
* A [relay](http://www.sparkfun.com/products/100) to control power to the strike
* A [buzzer](https://www.sparkfun.com/products/7950) (Optional)

## Arduino
### Wiring
Wiring is very simple, and can be done on a small breadboard. After attaching the Wifi Shield to the Arduino, all you have to do is to wire the `LOCK_RELAY` pin as defined in `rfid_lock.h` to your relay, `BUZZER` to your buzzer, `RFID_ENABLE` to the enable pin of the RFID Reader. The output pin of the RFID Reader will be connected to the Arduinos serial input (you can't have it connected while uploading the software) You probably also want to connect `RESET_PIN` to the reset pin of the Arduino, through a transistor as we have to reset the board from time to time due to the less than excellent stability of the Wifi Shield libraries.

### Software
The Arduino software is in the `arduino` directory. Download my fork of the [HTTPClient](https://github.com/salanki/HTTPClient) library and put in your `Arduino/libraries` directory. Rename `settings.h.example` to `settings.h` and update with your WiFi credentials and the address to the management server. To be able to use the "Open Lock" feature the web server needs to be able to access the lock on `LISTEN_PORT`. Compile and upload to your Arduino with the output from the RFID reader disconnected. After uploading connect the output from the RFID reader to the `Serial Rx` pin of the Arduino, and open a serial console at 2400bps to get status information.

## Management Server
The server is designed to run on [Heroku](https://www.heroku.com/) , uses PostgreSQL for storage and Google authenticator for user authentication.

![](https://raw.github.com/salanki/rfidlock/master/main_screenshot.png =250x)
![](https://raw.github.com/salanki/rfidlock/master/add_tag_screenshot.png)

### Installation
The server software is in the `web` directory. Setup a [Heroku](https://www.heroku.com/) App for the server. You probably want to read up on how to setup [play on heroku](http://www.playframework.com/documentation/2.2.x/ProductionHeroku). You need to update `conf/prod.conf` with the correct SQL server settings provided by Heroku. You also need to get an OAuth 2 key from [Google](https://developers.google.com/api-client-library/python/guide/aaa_oauth) as the server uses Google accounts user validation. The API keys go into `conf/securesocial.conf`. When the server is up and running you will manually have to edit user permissions in the SQL database after they have tried to log in once.

## License

MIT licensed

Copyright (C) 2014 Peter Salanki