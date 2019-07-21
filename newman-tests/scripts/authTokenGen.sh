#!/bin/sh

  IP=$(curl -X POST \
  https://hmda4.demo.cfpb.gov/auth/realms/hmda2/protocol/openid-connect/token \
  -H 'cache-control: no-cache' \
  -H 'content-type: application/x-www-form-urlencoded' \
  -H 'postman-token: 2ae356f7-7004-9b1a-992b-1cbb15f8179f' \
  -d 'client_id=hmda2-api&grant_type=password&username=bhaarat.sharma%40bank1.com&password=eagT%25731eagJ%5E831'| awk -F'"' '$2=="access_token"{print $4}')

echo $IP
