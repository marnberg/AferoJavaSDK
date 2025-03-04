/*
 * Copyright (c) 2014-2017 Afero, Inc. All rights reserved.
 */

package io.afero.sdk.client.mock;

import java.io.IOException;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;

import io.afero.sdk.client.afero.AferoClient;
import io.afero.sdk.client.afero.models.AccountDescriptionBody;
import io.afero.sdk.client.afero.models.AccountUserSummary;
import io.afero.sdk.client.afero.models.ActionResponse;
import io.afero.sdk.client.afero.models.ConclaveAccessDetails;
import io.afero.sdk.client.afero.models.DeviceAssociateResponse;
import io.afero.sdk.client.afero.models.DeviceInfoExtendedData;
import io.afero.sdk.client.afero.models.DeviceRules;
import io.afero.sdk.client.afero.models.DeviceTag;
import io.afero.sdk.client.afero.models.InvitationDetails;
import io.afero.sdk.client.afero.models.Location;
import io.afero.sdk.client.afero.models.PostActionBody;
import io.afero.sdk.client.afero.models.RuleExecuteBody;
import io.afero.sdk.client.afero.models.ViewRequest;
import io.afero.sdk.client.afero.models.ViewResponse;
import io.afero.sdk.client.afero.models.WriteRequest;
import io.afero.sdk.client.afero.models.WriteResponse;
import io.afero.sdk.conclave.models.DeviceSync;
import io.afero.sdk.device.DeviceModel;
import io.afero.sdk.device.DeviceProfile;
import rx.Observable;
import rx.subjects.PublishSubject;


public class MockAferoClient implements AferoClient {

    private final ResourceLoader mLoader;
    private DeviceAssociateResponse mDeviceAssociateResponse;
    private String mFileNameGetDevices = "getDevices.json";
    private Observable<WriteResponse[]> postBatchAttributeWriteResponse;
    private TimeZone mDeviceTimeZone;
    private int mRequestId;
    private HashMap<String,DeviceTag> mDeviceTags = new HashMap<>();
    private Throwable nextCallFailure;
    private String mViewingDeviceId;
    private long mViewingSeconds;
    private PublishSubject<ViewRequest> mViewRequestSubject = PublishSubject.create();

    public MockAferoClient() {
        mLoader = new ResourceLoader();
    }

    public MockAferoClient(String pathPrefix) {
        mLoader = new ResourceLoader(pathPrefix);
    }

    @Override
    public Observable<ActionResponse> postAttributeWrite(DeviceModel deviceModel, PostActionBody body, int maxRetryCount, int statusCode) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        ActionResponse response = new ActionResponse();
        return Observable.just(response);
    }

    @Override
    public Observable<WriteResponse[]> postBatchAttributeWrite(DeviceModel deviceModel, WriteRequest[] body, int maxRetryCount, int statusCode) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        if (postBatchAttributeWriteResponse == null) {
            WriteResponse[] response = new WriteResponse[body.length];
            for (int i = 0; i < response.length; ++i) {
                WriteResponse rr = new WriteResponse();
                rr.requestId = ++mRequestId;
                rr.status = WriteResponse.STATUS_SUCCESS;
                rr.timestampMs = System.currentTimeMillis();
                response[i] = rr;
            }
            return Observable.just(response);
        }

        return postBatchAttributeWriteResponse;
    }

    @Override
    public Observable<ViewResponse[]> postDeviceViewRequest(DeviceModel deviceModel, ViewRequest body) {
        mViewingDeviceId = deviceModel.getId();
        mViewingSeconds = body.seconds;
        mViewRequestSubject.onNext(body);
        return Observable.just(new ViewResponse[] { new ViewResponse() });
    }

    @Override
    public Observable<DeviceProfile> getDeviceProfile(String profileId) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        try {
            return Observable.just(mLoader.createObjectFromJSONResource(
                    "getDeviceProfile/" + profileId + ".json",
                    DeviceProfile.class));
        } catch (IOException e) {
            return Observable.error(e);
        }
    }

    @Override
    public Observable<DeviceProfile[]> getAccountDeviceProfiles() {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return Observable.fromCallable(new Callable<DeviceProfile[]>() {
            @Override
            public DeviceProfile[] call() throws Exception {
                return mLoader.createObjectFromJSONResource("getAccountDeviceProfiles.json", DeviceProfile[].class);
            }
        });
    }

    @Override
    public Observable<DeviceProfile> getDeviceProfilePreAssociation(String associationId, int version) {
        return null;
    }

    @Override
    public Observable<ConclaveAccessDetails> postConclaveAccess() {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return null;
    }

    @Override
    public Observable<ConclaveAccessDetails> postConclaveAccess(String mobileDeviceId) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return null;
    }

    @Override
    public Observable<Location> putDeviceLocation(String deviceId, Location location) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return Observable.just(location);
    }

    @Override
    public Observable<Location> getDeviceLocation(DeviceModel deviceModel) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return null;
    }

    @Override
    public Observable<DeviceTag> putDeviceTag(String deviceId, DeviceTag tag) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return Observable.fromCallable(new Callable<DeviceTag>() {

            DeviceTag deviceTag;

            Callable<DeviceTag> init(DeviceTag tag) {
                deviceTag = tag;
                return this;
            }

            @Override
            public DeviceTag call() throws Exception {
                DeviceTag oldTag = mDeviceTags.get(deviceTag.deviceTagId);
                if (oldTag == null) {
                    throw new Exception("404");
                }

                oldTag.key = deviceTag.key;
                oldTag.value = deviceTag.value;

                return oldTag;
            }
        }.init(tag));
    }

    @Override
    public Observable<DeviceTag> postDeviceTag(String deviceId, String tagKey, String tagValue) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        DeviceTag tag = new DeviceTag(tagKey, tagValue);

        return Observable.fromCallable(new Callable<DeviceTag>() {

            DeviceTag deviceTag;

            Callable<DeviceTag> init(DeviceTag tag) {
                deviceTag = tag;
                return this;
            }

            @Override
            public DeviceTag call() throws Exception {
                deviceTag.deviceTagId = UUID.randomUUID().toString();
                mDeviceTags.put(deviceTag.deviceTagId, deviceTag);
                return deviceTag;
            }
        }.init(tag));
    }

    @Override
    public Observable<Void> deleteDeviceTag(String deviceId, final String tagId) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return Observable.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                if (mDeviceTags.remove(tagId) == null) {
                    throw new Exception("404");
                }
                return null;
            }
        });
    }

    @Override
    public Observable<AccountDescriptionBody> putAccountDescription(String accountId, AccountDescriptionBody body) {
        return null;
    }

    @Override
    public Observable<DeviceAssociateResponse> deviceAssociateGetProfile(String associationId, boolean isOwnershipVerified) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        try {
            DeviceAssociateResponse dar = mDeviceAssociateResponse;
            if (dar == null) {
                dar = mLoader.createObjectFromJSONResource("deviceAssociate/" + associationId + ".json", DeviceAssociateResponse.class);
            }
            return Observable.just(dar);
        } catch (IOException e) {
            return Observable.error(e);
        }
    }

    @Override
    public Observable<DeviceAssociateResponse> deviceAssociate(String associationId) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        try {
            DeviceAssociateResponse dar = mDeviceAssociateResponse;
            if (dar == null) {
                dar = mLoader.createObjectFromJSONResource("deviceAssociate/" + associationId + ".json", DeviceAssociateResponse.class);
            }
            return Observable.just(dar);
        } catch (IOException e) {
            e.printStackTrace();
            return Observable.error(e);
        }
    }

    @Override
    public Observable<DeviceModel> deviceDisassociate(DeviceModel deviceModel) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return Observable.just(deviceModel);
    }

    @Override
    public Observable<DeviceInfoExtendedData> getDeviceInfo(String deviceId) {
        return null;
    }

    @Override
    public Observable<Void> putDeviceTimeZone(DeviceModel deviceModel, TimeZone tz) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return Observable.just(null);
    }

    @Override
    public Observable<TimeZone> getDeviceTimeZone(DeviceModel deviceModel) {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return mDeviceTimeZone != null ? Observable.just(mDeviceTimeZone) : Observable.<TimeZone>empty();
    }

    @Override
    public Observable<DeviceSync[]> getDevicesWithState() {
        if (hasNextCallFailure()) {
            return nextCallFailObservable();
        }

        return Observable.fromCallable(new Callable<DeviceSync[]>() {
            @Override
            public DeviceSync[] call() throws Exception {
                return mLoader.createObjectFromJSONResource(mFileNameGetDevices, DeviceSync[].class);
            }
        });
    }

    @Override
    public Observable<ActionResponse[]> ruleExecuteActions(String ruleId, RuleExecuteBody body) {
        return null;
    }

    @Override
    public Observable<DeviceRules.Rule[]> getDeviceRules(String deviceId) {
        return null;
    }

    @Override
    public Observable<DeviceRules.Rule[]> getAccountRules() {
        return null;
    }

    @Override
    public Observable<DeviceRules.Schedule> putSchedule(String scheduleId, DeviceRules.Schedule schedule) {
        return null;
    }

    @Override
    public Observable<DeviceRules.Schedule> postSchedule(DeviceRules.Schedule schedule) {
        return null;
    }

    @Override
    public Observable<DeviceRules.Rule> postRule(DeviceRules.Rule rule) {
        return null;
    }

    @Override
    public Observable<DeviceRules.Rule> putRule(String ruleId, DeviceRules.Rule rule) {
        return null;
    }

    @Override
    public Observable<Void> deleteRule(String ruleId) {
        return null;
    }

    @Override
    public Observable<AccountUserSummary> getAccountUserSummary() {
        return null;
    }

    @Override
    public Observable<Void> postInvite(InvitationDetails invite) {
        return null;
    }

    @Override
    public Observable<Void> deleteInvite(String invitationId) {
        return null;
    }

    @Override
    public Observable<Void> deleteUser(String userId) {
        return null;
    }

    @Override
    public String getActiveAccountId() {
        return null;
    }

    @Override
    public int getStatusCode(Throwable t) {
        return 0;
    }

    @Override
    public boolean isTransferVerificationError(Throwable t) {
        return false;
    }

    public void failNextCall(Throwable t) {
        nextCallFailure = t;
    }

    public void setDeviceAssociateResponse(DeviceAssociateResponse dar) {
        mDeviceAssociateResponse = dar;
    }

    public void clearDeviceAssociateResponse() {
        mDeviceAssociateResponse = null;
    }

    public void setFileGetDevices(String file) {
        mFileNameGetDevices = file;
    }

    public void setPostBatchAttributeWriteResponse(Observable<WriteResponse[]> response) {
        postBatchAttributeWriteResponse = response;
    }

    public void setDeviceTimeZone(TimeZone tz) {
        mDeviceTimeZone = tz;
    }

    public DeviceTag getTagById(String deviceTagId) {
        return mDeviceTags.get(deviceTagId);
    }

    private <T> Observable<T> nextCallFailObservable() {
        return Observable.error(nextCallFailure);
    }

    private boolean hasNextCallFailure() {
        return nextCallFailure != null;
    }

    public String getViewingDeviceId() {
        return mViewingDeviceId;
    }

    public long getViewingDeviceSeconds() {
        return mViewingSeconds;
    }

    public Observable<ViewRequest> observeViewRequests() {
        return mViewRequestSubject;
    }
}
