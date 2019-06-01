# landblocked

poc 2...

Requires server backend landblocked-websocket which is a play framework project that via websocket handles players comms.

To use that 

```touch $HOME/gamelogic_local.txt```

otherwise it will run against Heroku deployment

https://landedblocked.herokuapp.com

```
./gradlew desktop:run
```

or for executable

```
./gradlew desktop:dist

java -jar desktop/build/libs/desktop-1.0.jar
```



