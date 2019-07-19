FROM node:8.16.0-alpine as build-stage

ARG user

RUN echo "Oh dang look at that $user"

#RUN mkdir -p /home/newman-trials/scripts/hmda-filing/

#WORKDIR /home/newman-trials

#COPY hmda-filing/*  scripts/hmda-filing/
#COPY newman-pak/newman-4.5.1.tgz  .

#RUN  ls -l
#RUN npm i  --prefix ./ newman-4.5.1.tgz
#RUN ./node_modules/.bin/newman -v

#RUN ./node_modules/.bin/newman run scripts/hmda-filing/hmda-filing-api-test.json -d scripts/hmda-filing/hmda-filing-api-config.json --env-var user="admin" --env-var password="admin123"
