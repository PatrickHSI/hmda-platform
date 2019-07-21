FROM node:8.16.0-alpine as build-stage


RUN mkdir -p /home/newman-trials/scripts/hmda-filing/

WORKDIR /home/newman-trials

COPY hmda-filing/*  scripts/hmda-filing/
COPY scripts/*  scripts/
COPY newman-pak/newman-4.5.1.tgz  .

RUN ["chmod", "+x", "scripts/authTokenGen.sh"]

RUN  cd scripts && cat authTokenGen.sh

#RUN npm i  --prefix ./ newman-4.5.1.tgz
#RUN ./node_modules/.bin/newman -v

#RUN ./node_modules/.bin/newman run scripts/hmda-filing/hmda-filing-api-test.json -d scripts/hmda-filing/hmda-filing-api-config.json --env-var user="admin" --env-var password="admin123"
