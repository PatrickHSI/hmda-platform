FROM node:8.16.0-alpine as build-stage



RUN mkdir -p /home/newman-trials/scripts/hmda-filing/

WORKDIR /home/newman-trials

COPY hmda-filing/*  scripts/hmda-filing/
COPY newman-pak/newman-4.5.1.tgz  .

RUN  ls -l
RUN npm i  --prefix ./ newman-4.5.1.tgz
RUN newman -v
