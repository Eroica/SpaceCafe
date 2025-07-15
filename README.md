# SpaceCafe

SpaceCafe is a Gemini server written in Kotlin using coroutines. It is based on _SpaceBeans_, written in Scala, by [@reidrac](https://github.com/reidrac).

Since SpaceBeans development has stopped, I decided to convert it to Kotlin.

It should more or less support all of [SpaceBean's features](https://git.usebox.net/spacebeans/about/).

## How to run

You need to create a `spacecafe.conf` configuration file, and a certificate. Take a look at the example configuration to see how to configure the server.

[Download](https://github.com/Eroica/SpaceCafe/releases) or build the SpaceCafe JAR file, and then you can run: `java -jar spacecafe-VERSION.jar -c myconfig.conf`

### Differences to SpaceBean's config

* `idle-timeout` must be specified as an integer of milliseconds (`10000`) instead of a string like `10 seconds`
* If no config file is specified, the default one is only searched in the current directory instead of something like `/etc/...`

### Create a certificate

Using `keytool`:

`keytool -genkey -keyalg RSA -alias ALIAS -keystore keystore.jks -storepass SECRET -noprompt -validity 36500 -keysize 2048`

When prompted, enter the domain name as the first "name" (_CN_). For example, if you run and test the server locally, just `localhost`.

## Development

SpaceCafe is written in Kotlin and built with Gradle. `jvmToolchain` is currently set to 21.

* `gradle build` to build the project
* `gradle run` to build and run the server
* `gradle shadowJar` to build a fat JAR (contains all dependencies)

Note that by default, a configuration file `spacecafe.conf` is expected in the working directory.

## License

SpaceCafe is published under the zlib license. See `LICENSE` file.

```
Copyright (C) 2025 Eroica

This software is provided 'as-is', without any express or implied
warranty. In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
```
