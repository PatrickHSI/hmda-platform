FROM node:8.16.0-jessie as build-stage

RUN mkdir -p /home/newman-trials/scripts/hmda-filing/

WORKDIR /home/newman-trials

COPY hmda-filing/*  scripts/hmda-filing/
COPY scripts/*  scripts/
COPY newman-pak/newman-4.5.1.tgz  .
 
RUN ["ping", "https://hmda4.demo.cfpb.gov", "-c 4"]
#RUN ["chmod", "u+x", "scripts/authTokenGen.sh"]
#RUN  cat scripts/authTokenGen.sh
#RUN ./scripts/authTokenGen.sh

#RUN  echo $IP
#RUN npm i  --prefix ./ newman-4.5.1.tgz
#RUN ./node_modules/.bin/newman -v

#RUN ./node_modules/.bin/newman run scripts/hmda-filing/hmda-filing-api-test.json -d scripts/hmda-filing/hmda-filing-api-config.json --env-var user="admin" --env-var password="admin123"
