FROM clojure

RUN mkdir /app

WORKDIR /app

COPY project.clj /app/
COPY Makefile /app/

RUN make init

COPY . /app

RUN make build

EXPOSE 3000

CMD ["java", "-jar", "target/uberjar/patients-standalone.jar"]
