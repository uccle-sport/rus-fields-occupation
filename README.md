## Purpose of this project

The purpose of this project is to provide a tool to import data from Sportlink and inject it into Google Calendar.

## How to use

### Prerequisites

* Java 17
* You need an OAuth2 client ID and secret. See [here](https://developers.google.com/calendar/quickstart/java) for more information.

### Configuration

Copy the OAuth2 client ID and secret (stored in a file called `client_oauth_secret.json`) to the `src/main/resources` folder.

### Run

run `./gradlew run` to start the application.

## How to build

run `./gradlew build` to build the application.