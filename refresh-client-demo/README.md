# routex-refresh-client demo

Two independent runnable examples, Java and Kotlin, showing how to refresh account balances with `tech.yaxi:routex-refresh-client`.

## Get an API key

Sign up at https://yaxi.tech and create an API key for the **Integration** environment.
Your key id and base64-encoded secret feed the env vars below.
The Demo Bank connection used by this project is only available on the Integration environment, not on production.

## Run

Requires JDK 21 or newer.

1. Set the three required environment variables:

   ```sh
   export YAXI_API_URL=https://integration.yaxi.tech
   export YAXI_API_KEY_ID=<your-key-id>
   export YAXI_API_KEY_SECRET=<your-base64-secret>
   ```

2. Run either demo:

   ```sh
   ./gradlew :java-demo:run
   ./gradlew :kotlin-demo:run
   ```

Both produce identical output.

## What it does

Each demo first opens a one-shot consent against YAXI's Demo Bank to obtain a `ConnectionData`.
This is the part your frontend would normally do via SCA.
It then uses `RoutexRefreshClient` with that `ConnectionData` to fetch fresh balances non-interactively.
The refresh step is the only part a backend service typically runs in production.
The consent half is included here so the demo is self-contained.

## Pick your language

`java-demo/` targets JDK 21 and uses `RoutexRefreshAsyncClient` (`CompletableFuture`).
`kotlin-demo/` uses Kotlin 2.4 and `RoutexRefreshClient` (`suspend`).
Each module is fully self-contained; copy whichever fits your stack.
