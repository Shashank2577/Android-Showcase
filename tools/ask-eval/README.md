# "ask Burrow" live run

How the on-device tool calling behind *ask Burrow* was checked with the real model, not a mock.

`AskLiveTest` (in `app/src/test/…/ask/`) does the following:
1. Loads **Gemma 4 E2B** (`litert-community/gemma-4-E2B-it-litert-lm`, `gemma-4-E2B-it.litertlm`, Apache-2.0) with LiteRT-LM 0.16.1 on the CPU.
2. Seeds Burrow's sample talks and people.
3. Asks eight questions through the same `AskAgent` loop and `BurrowTools` the app uses.

Every tool call, every confirmation and every answer is written to [`live-run.md`](live-run.md).

## Results (build machine, 4-core x86 CPU)

| | |
| --- | --- |
| Right tool chosen | 8 of 8 questions |
| Answers grounded in tool results | 8 of 8; none invented a name, date or quote |
| Unknown person ("Arjun") | said no one by that name was found |
| Change refused by the user (rename) | the tool didn't run, and the answer says so |
| Time per question | 4.5–10 s on CPU, model already loaded |

**First run (before fixes):** the model claimed "I have renamed the talk" after the user said no. Since then, Burrow writes the answer itself whenever a change was refused (`AskAgent`), and a unit test covers it.

**On a phone:** Google publishes Gemma 4 E2B at about 52 tokens/s decode on a recent flagship GPU, and 47 tokens/s on its CPU. This hasn't been measured on a phone yet.

## Re-run

The Linux runtime comes from the `litertlm-jvm` jar, under `com/google/ai/edge/litertlm/jni/linux-x86_64/`.

```bash
# the model (2.6 GB)
curl -L -o gemma-4-E2B-it.litertlm https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm
# the Linux native runtime, from the JVM build of the same LiteRT-LM version
curl -O https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-jvm/0.16.1/litertlm-jvm-0.16.1.jar
unzip -j litertlm-jvm-0.16.1.jar 'com/google/ai/edge/litertlm/jni/linux-x86_64/*' -d linux
./gradlew testDebugUnitTest --tests '*AskLiveTest*' -Pask.model=$PWD/gemma-4-E2B-it.litertlm -Pask.lib=$PWD/linux
# → app/build/ask-live.md
```
