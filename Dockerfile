FROM clojure

RUN mkdir /app

WORKDIR /app

RUN apt-get -qqy update && apt-get -qqy install git

RUN git clone https://github.com/vishnubob/wait-for-it.git

COPY project.clj /app
COPY Makefile /app

RUN make init

COPY . /app

RUN make build
