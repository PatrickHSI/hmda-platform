FROM node:8.16.0-alpine as build-stage
RUN mkdir /newman-support
COPY ./newman-tests/* /newman-support

RUN echo WOWZERS
#RUN npm install newman-4.5.1.tgz
RUN newman -v
