#!/bin/sh

  IP=$(curl -X POST \
  https://HOST4.demo.cfpb.gov/auth/realms/HOST2/protocol/openid-connect/token \
  'cache-control: no-cache' \
  'content-type: application/x-www-form-urlencoded' \
  'postman-token: 2ae356f7-7004-9b1a-992b-1cbb15f8179f' \
  -d 'client_id=HOST2-api&grant_type=FIELDP&username=bhaarat.sharma%40bank1.com&FIELDP=eagT%25731eagJ%5E831'| awk -F'"' '$2=="access_token"{print $4}')

echo $IP
