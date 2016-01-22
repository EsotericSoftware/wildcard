![](https://raw.github.com/wiki/EsotericSoftware/wildcard/images/logo.png)

Please use the [WildCard discussion group](http://groups.google.com/group/wildcard-users) for support.

Wildcard is a small Java library that performs efficient pattern matching of files and directories. Paths can be matched with wildcards or regular expressions. Matched files can be easily copied, deleted, zipped, etc.

## Glob matching

The `glob` method collects files and directories using literal characters and optional wildcards:

    Paths paths = new Paths();
    paths.glob("/some/directory", "resources");
    paths.glob("/some/directory", "images/**/*.jpg", "!**/.svn/**");

The first parameter defines the root directory of the search. Subsequent parameters are a variable number of search patterns. The following wildcards are supported in search patterns:

<table>
  <tr><td>?</td><td>Matches any single character. Eg, "something?" collects any path that is named "something" plus any character.</td></tr>
  <tr><td>*</td><td>Matches any characters up to the next slash. Eg, "*/*/something*" collects any path that has two directories, then a file or directory that starts with the name "something".</td></tr>
  <tr><td>**</td><td>Matches any characters. Eg, "**/something/**" collects any path that contains a directory named "something".</td></tr>
  <tr><td>!</td><td>A pattern starting with an exclamation point (!) causes paths matched by the pattern to be excluded, even if other patterns would select the paths.</td></tr>
</table>

When using `glob`, the search is done as efficiently as possible. Directories are not traversed if none of the search patterns can match them.

Glob is also used when constructor parameters are specified:

    Paths paths = new Paths("/some/directory", "resources");

## Regex matching

Regular expressions can be used to collect files and directories:

    Paths paths = new Paths();
    paths.regex("/some/directory", "images.*\\.jpg", "!.*/\\.svn/.*");

Regex patterns that being with `!` caused matched paths to be excluded, even if other patterns would select the paths.

Regular expressions have more expressive power, but it comes at a price. When using `regex`, the search is not done as efficiently as with `glob`. All directories and files under the root are traversed, even if none of the search patterns can match them.

## Pipe delimited patterns

If `glob` or `regex` is passed only one parameter, it may be a root directory and then any number of search patterns, delimited by pipe (|) characters:

    Paths paths = new Paths();
    paths.glob("/some/directory|resources");
    paths.glob("/some/directory|images/**/*.jpg|!**/.svn/**");

This is useful in cases where it is more convenient to use a single string to describe what files to collect.

If `glob` is passed only one parameter that is not pipe delimited, or if only exclude patterns are specified (using the `!` character), then an additional search pattern of `**` is implied.

## Utility methods

The `glob` and `regex` methods can be called repeatedly to collect paths from different root directories. Internally, a `Paths` instance holds all the paths matched and remembers each root directory where the search was performed. This greatly simplifies many tasks. The `Paths` class has utility methods for manipulating the paths, eg:

    Paths paths = new Paths();
    paths.glob("/some/directory", "**/images/*/image0?.*");
    paths.copyTo("/another/directory");

This collects all JPG files in any directory under "/some/directory". It then copies those files to "/another/directory". Note that the directory structure under the root directory is preserved. Eg, if you had these files:

    /some/directory/stuff.jpg
    /some/directory/otherstuff.gif
    /some/directory/animals/cat.jpg
    /some/directory/animals/dog.jpg
    /some/directory/animals/giraffe.tga

The result after the copy would be:

    /another/directory/stuff.jpg
    /another/directory/animals/cat.jpg
    /another/directory/animals/dog.jpg

The `Paths` class has methods to copy, delete, and zip the paths. It also has methods to obtain the individual paths in various ways, so you can take whatever action you like:

    for (String fullPath : new Paths(".", "*.png")) { ... }
    for (String dirName : new Paths(".").dirsOnly().getNames()) { ... }
    for (File file : new Paths(".", "*.jpg").getFiles()) { ... )
