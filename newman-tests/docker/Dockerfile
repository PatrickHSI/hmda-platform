FROM alpine:3.8

RUN apk add --update libzip-dev curl-dev &&\
    docker-php-ext-install curl && \
    apk del gcc g++ &&\
    rm -rf /var/cache/apk/*
#FROM node:8.16.0-alpine as build-stage

#RUN apk --no-cache add curl

#RUN apt-get update; apt-get install curl

#RUN mkdir -p /home/newman-trials/scripts/hmda-filing/

#WORKDIR /home/newman-trials

#COPY hmda-filing/*  scripts/hmda-filing/
#COPY scripts/*  scripts/
#COPY newman-pak/newman-4.5.1.tgz  .

#RUN ["chmod", "u+x", "scripts/authTokenGen.sh"]

#RUN ./scripts/authTokenGen.sh

#RUN npm i  --prefix ./ newman-4.5.1.tgz
#RUN ./node_modules/.bin/newman -v

#RUN ./node_modules/.bin/newman run scripts/hmda-filing/hmda-filing-api-test.json -d scripts/hmda-filing/hmda-filing-api-config.json --env-var user="admin" --env-var password="admin123"
