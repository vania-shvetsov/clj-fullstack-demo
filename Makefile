init:
	lein deps

repl:
	lein repl

build-client:
	rm -rf resources/public/cljs-out/*
	lein cljsbuild once

build-server:
	lein clean
	lein uberjar

build: build-client build-server

migrate:
	lein migrate

rollback:
	lein rollback

new-migration:
	lein new-migration $(name)

pending-migrations:
	lein pending-migrations
