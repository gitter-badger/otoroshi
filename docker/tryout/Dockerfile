# Not for a production usage, just a simple playground to show what you can do with Otoroshi
FROM openjdk:8

MAINTAINER MAIF <oss@maif.fr>

ENV APP_NAME otoroshi-tryout
ENV APP_VERSION 1.0.0

RUN mkdir -p /usr/app

WORKDIR /usr/app

COPY ./entrypoint.sh /usr/app

RUN wget --quiet 'https://dl.bintray.com/mathieuancelin/otoroshi/latest/otoroshi.jar'

RUN chmod +x /usr/app/entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]

EXPOSE 8080

CMD