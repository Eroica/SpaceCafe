# SpaceCafe

SpaceCafe is a Gemini server written in Kotlin using coroutines. It is based on _SpaceBeans_, written in Scala, by [@reidrac](https://github.com/reidrac).

Since SpaceBeans development has stopped, I decided to convert it to Kotlin.

### Differences to SpaceBean's config

* `idle-timeout` must be specified as an integer of milliseconds (`10000`) instead of a string like `10 seconds`
