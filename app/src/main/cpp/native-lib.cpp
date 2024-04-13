#include <jni.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include "sox.h"

#define APPNAME "AudioBookPlayer"

#define RESULT_SUCCESS 0
#define RESULT_ERROR -1

int sox_tempo(char* inPathCStr, char* outPathCStr, char* tempoCStr);

extern "C" JNIEXPORT int JNICALL
Java_jatx_audiobookplayer_PlayerService_applyTempoJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring inPath,
        jstring outPath,
        jstring tempo
        ) {
    char* inPathCStr;
    char* outPathCStr;
    char* tempoCStr;
    int result;
    inPathCStr = (char*) env->GetStringUTFChars(inPath, NULL);
    outPathCStr = (char*) env->GetStringUTFChars(outPath, NULL);
    tempoCStr = (char*) env->GetStringUTFChars(tempo, NULL);
    result = sox_tempo(inPathCStr, outPathCStr, tempoCStr);
    env->ReleaseStringUTFChars(inPath, inPathCStr);
    env->ReleaseStringUTFChars(outPath, outPathCStr);
    env->ReleaseStringUTFChars(tempo, tempoCStr);
    return result;
}

int sox_tempo(char* inPathCStr, char* outPathCStr, char* tempoCStr) {
    static sox_format_t * in, * out; /* input and output files */
    sox_effects_chain_t * chain;
    sox_effect_t * e;
    sox_signalinfo_t interm_signal;
    char * args[10];

    /* All libSoX applications must start by initialising the SoX library    */
    if(sox_init() != SOX_SUCCESS) {
        return RESULT_ERROR;
    }

    /* Open the input file (with default parameters) */
    in = sox_open_read(inPathCStr, NULL, NULL, NULL);
    if (!in) {
        return RESULT_ERROR;
    }

    interm_signal = in->signal;

    /* Open the output file; we must specify the output signal characteristics.
    * Since we are using only simple effects, they are the same as the input
    * file characteristics */
    out = sox_open_write(outPathCStr, &interm_signal, NULL, NULL, NULL, NULL);
    if (!out) {
        return RESULT_ERROR;
    }

    /* Create an effects chain; some effects need to know about the input
    * or output file encoding so we provide that information here */
    chain = sox_create_effects_chain(&in->encoding, &out->encoding);

    /* The first effect in the effect chain must be something that can source
    * samples; in this case, we use the built-in handler that inputs
    * data from an audio file */
    e = sox_create_effect(sox_find_effect("input"));
    args[0] = (char *)in;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* This becomes the first `effect' in the chain */
    if(sox_add_effect(chain, e, &interm_signal, &in->signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Create the `tempo' effect, and initialise it with the desired parameters: */
    e = sox_create_effect(sox_find_effect("tempo"));
    args[0] = tempoCStr;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    /* Add the effect to the end of the effects processing chain: */
    if(sox_add_effect(chain, e, &interm_signal, &out->signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* The last effect in the effect chain must be something that only consumes
    * samples; in this case, we use the built-in handler that outputs
    * data to an audio file */
    e = sox_create_effect(sox_find_effect("output"));
    args[0] = (char *)out;
    if(sox_effect_options(e, 1, args) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    if(sox_add_effect(chain, e, &interm_signal, &out->signal) != SOX_SUCCESS) {
        return RESULT_ERROR;
    }
    free(e);

    /* Flow samples through the effects processing chain until EOF is reached */
    sox_flow_effects(chain, NULL, NULL);

    /* All done; tidy up: */
    sox_delete_effects_chain(chain);
    sox_close(out);
    sox_close(in);
    sox_quit();

    __android_log_print(ANDROID_LOG_ERROR, APPNAME, "Tempo done: %s", tempoCStr);

    return RESULT_SUCCESS;
}
