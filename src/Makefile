.PHONY: clean build watch

clean:
	stack exec site clean

build: clean
	stack exec site rebuild

watch: build
	stack exec site watch
