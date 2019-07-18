FROM node:8.16.0-alpine as build-stage

# install build dependencies
COPY package.json yarn.lock .yarnrc ./
# install packages offline
COPY npm-packages-offline-cache ./npm-packages-offline-cache
RUN yarn install