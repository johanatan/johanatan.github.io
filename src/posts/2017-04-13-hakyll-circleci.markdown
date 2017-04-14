---
title: Hakyll on GitHub Pages with Continuous Deployment via CircleCI
description: Setting up a Hakyll blog on GitHub Pages with Stack and CircleCI
---

## Motivation ##

After fighting with Jekyll for a few days, I decided to seek out alternatives.
One great suggestion I received (from @[davenpcm](https://twitter.com/davenpcm))
was Hakyll (which I had not known about until
that time). So naturally I searched the web for examples of using Hakyll with
GitHub Pages (as I wasn't keen to try other blog providers and very much like the
simplicity of GitHub Pages' model).

After assembling my solution from a hodge podge of other guides (& after
attempting to follow [one](https://www.stackbuilders.com/news/dr-hakyll-create-a-github-page-with-hakyll-and-circleci)
of them to the letter [with a minor exception being
that Hakyll's default mode of compilation these days seems to utilize Stack
rather than, as the guide expected, Cabal (which is perfectly fine to me as it does seem like the
nicer tool)]), I decided to share it as others
attempting to go down this road could certainly benefit from the reduction.

## Enable GitHub Pages ##

The straightforward guide at [GitHub](https://pages.github.com/)
should be sufficient to get a GitHub Page for either your site or your organization
up and running. My example below will pertain to a user/organization but it can be
easily modified to serve a project site by substituting `gh-pages` for any
appearance of `master` (in the context of a reference to a branch).

## Create (or transplant) your Hakyll site generator

From the root of your repository, create a folder to host your Hakyll
installation & content.

```
$ mkdir src/
```

Inside this directory, add the following [`Makefile`](https://github.com/johanatan/johanatan.github.io/blob/master/src/Makefile):

```
.PHONY: clean build watch

clean:
	stack exec site clean

build:
	stack build --fast
	stack build --fast --pedantic --haddock --test --no-run-tests --no-haddock-deps
	stack exec site rebuild

watch: build
	stack exec site watch
```

and [`.gitignore`](https://github.com/johanatan/johanatan.github.io/blob/master/src/.gitignore):
```
.stack-work/
_site/
_cache/
```

as well as the working Hakyll generator & content.
[Here's](https://github.com/johanatan/johanatan.github.io/tree/master/src)
the one being used to generate this site. 

## CircleCI (Continuous Deployment)

Rather than going with a (complicated & error prone) multiple branch and submodule
solution as outlined in the aforementioned [guide](https://www.stackbuilders.com/news/dr-hakyll-create-a-github-page-with-hakyll-and-circleci), I decided to opt for a little
bash-fu in the circle.yml file that, upon deployment, removes the previously generated
files and copies the new ones into their place (in the root of the repository as
GitHub Pages requires).

The key to this was the combination of (appropriately switched) `find`, `xargs` and
`rm`, followed up with a (correctly modified) `cp` & `git add`. i.e., the following
relevant lines from my [`circle.yml`](https://github.com/johanatan/johanatan.github.io/blob/master/circle.yml).

```
- cd src/ && find .. -maxdepth 1 -type d | grep -v .git | grep -v ../src | grep -v ^\.\.$ | xargs -r rm -r
- cd src/ && find .. -maxdepth 1 -type f | grep -v .git | grep -v circle\.yml | xargs -r rm
...
- cd src/ && make build && cp -a _site/. ..
- git status && git add -A .

```

The trailing `.` on `_site/.` is required on the Linux running on CircleCI although on
Mac OS X, trailing slash is sufficient/recommended by the man page. Also, the `-a` on
the `cp` invokes the `-R` mode (short for recursive presumably) which grabs the entire
subtree rooted at the specified directory.

Also of note here is the `-A` on `git add` which does the right thing when files have
been added, updated or deleted (as would be the case with generated files).

The `dependencies` section of `circle.yml` came from a combination of the following
two examples:

* [haskell-circle-example](https://github.com/begriffs/haskell-circle-example/blob/master/circle.yml)
* [Haskell project with Stack on CircleCI](https://gist.github.com/dbp/bef96402ea07001dfed2)

## Concluding Thoughts ##

So, there we have it: a Hakyll-generated, continuously-deployed static site elevated
to [essentially] first-class status on the GitHub Pages platform (with the exception
being that CircleCI does our building behind the scenes rather than GitHub itself
[which in practice is inconsequential]). Along with this comes all of the niceties
that people love about hosting on GitHub Pages with the primary one being: edit
on any device, from any location directly in GitHub and have the site automatically
updated within a handful of minutes (for my first few edits this has typically been
in the range of 2-3 minutes).

A link to the full source code for this site is provided at the bottom of this (and every other)
page on the site. Enjoy!
