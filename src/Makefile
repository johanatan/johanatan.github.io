.PHONY: clean build watch

clean:
	stack exec site clean

build:
	stack build
	stack exec site rebuild

watch: build
	stack exec site watch
