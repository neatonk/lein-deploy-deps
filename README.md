# lein-deploy-deps

Deploy all of your project's dependencies to a remote repository with
[Leiningen][0].

## Install

### Leiningen 2

Add `[lein-deploy-deps "0.1.0"]` to the `:plugins` vector of your
`:user` profile, or your `project.clj`.

### Leiningen 1

This has not been tested. If you use `lein-deploy-deps` with leiningen 1 please
let me know how it goes.


    $ lein plugin install lein-deploy-deps 0.1.0

## Usage

    $ lein deploy-deps

OR

    $ lein deploy-deps releases snapshots

Either command will deploy all of your projects dependencies to the `releases`
and `snapshots` repositories set up in your `project.clj` which should look
something like this:

```clojure
{:deploy-repositories [["snapshots" {:url "https://your-repo.org/snapshots"}]
                       ["releases" {:url "https://your-repo.org/releases"}]]}
```

OR

```clojure
{repositories [["snapshots" {:url "https://your-repo.org/snapshots"}]
               ["releases" {:url "https://your-repo.org/releases"}]]}
```

You can use any repository that you've set up in your `project.clj`. For
repositories not named `"snapshots"` or `"releases"` you should make sure to set
`:snapshots` appropriately.

## Thanks!

This plugin was developed with the support of [otherpeoplespixels][1].

## License

Copyright © 2013 Kevin Neaton

Distributed under the [Eclipse Public License][2].

[0]: https://github.com/technomancy/leiningen
[1]: http://www.otherpeoplespixels.com
[2]: http://www.eclipse.org/legal/epl-v10.html
