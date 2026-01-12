import Java from 'frida-java-bridge';

const NONCE = NONCE_PLACEHOLDER;
const PROJECT_NUM = PROJECT_NUM_PLACEHOLDER;

Java.perform(function() {
    try {
        const ActivityThread = Java.use('android.app.ActivityThread');
        const context = ActivityThread.currentApplication().getApplicationContext();
        const IntegrityManagerFactory = Java.use('com.google.android.play.core.integrity.IntegrityManagerFactory');
        const IntegrityTokenRequest = Java.use('com.google.android.play.core.integrity.IntegrityTokenRequest');

        const manager = IntegrityManagerFactory.create(context);
        const request = IntegrityTokenRequest.builder()
            .setNonce(NONCE)
            .setCloudProjectNumber(PROJECT_NUM)
            .build();

        const task = manager.requestIntegrityToken(request);

        const OnSuccessListener = Java.registerClass({
            name: 'com.example.SuccessListener',
            implements: [Java.use('com.google.android.gms.tasks.OnSuccessListener')],
            methods: {
                onSuccess: function(result: any) {
                    const IntegrityTokenResponse = Java.use('com.google.android.play.core.integrity.IntegrityTokenResponse');
                    const response = Java.cast(result, IntegrityTokenResponse);
                    send({type: 'token', value: response.token()});
                }
            }
        });

        const OnFailureListener = Java.registerClass({
            name: 'com.example.FailureListener',
            implements: [Java.use('com.google.android.gms.tasks.OnFailureListener')],
            methods: {
                onFailure: function(exception: any) {
                    send({type: 'error', value: exception.toString()});
                }
            }
        });

        task.addOnSuccessListener(OnSuccessListener.$new());
        task.addOnFailureListener(OnFailureListener.$new());
    } catch (e: any) {
        send({type: 'error', value: e.stack || e.toString()});
    }
});