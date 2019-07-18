FROM node:8.16.0-alpine as build-stage



RUN mkdir -p /home/newman-trials/scripts/hmda-filing/

WORKDIR /home/newman-trials

#Copy all the required files from host's file system to the container file system.
COPY hmda-filing/*  scripts/hmda-filing/
COPY newman-pak/newman-4.5.1.tgz  .

RUN  ls -l
RUN  cd scripts/hmda-filing &&  ls -l
RUN echo WOWZERS
#RUN npm install newman-4.5.1.tgz
#RUN newman -v
