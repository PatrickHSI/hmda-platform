FROM node:8.16.0-alpine as build-stage
COPY newman-tests/newman-4.5.1.tgz ./

RUN echo WOWZERS
RUN npm install newman-4.5.1.tgz
RUN newman -v
