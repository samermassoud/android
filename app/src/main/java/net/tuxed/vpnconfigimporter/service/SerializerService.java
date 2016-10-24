package net.tuxed.vpnconfigimporter.service;

import android.util.Pair;

import net.tuxed.vpnconfigimporter.entity.DiscoveredAPI;
import net.tuxed.vpnconfigimporter.entity.Instance;
import net.tuxed.vpnconfigimporter.entity.InstanceList;
import net.tuxed.vpnconfigimporter.entity.Profile;
import net.tuxed.vpnconfigimporter.entity.SavedProfile;
import net.tuxed.vpnconfigimporter.entity.SavedToken;
import net.tuxed.vpnconfigimporter.entity.Settings;
import net.tuxed.vpnconfigimporter.entity.message.Maintenance;
import net.tuxed.vpnconfigimporter.entity.message.Message;
import net.tuxed.vpnconfigimporter.entity.message.Notification;
import net.tuxed.vpnconfigimporter.utils.Log;
import net.tuxed.vpnconfigimporter.utils.TTLCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * This service is responsible for (de)serializing objects used in the app.
 * Created by Daniel Zolnai on 2016-10-12.
 */
public class SerializerService {
    public class UnknownFormatException extends Exception {
        UnknownFormatException(String message) {
            super(message);
        }

        UnknownFormatException(Throwable throwable) {
            super(throwable);
        }
    }

    private static final String TAG = SerializerService.class.getName();
    private static final DateFormat API_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    static {
        API_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }


    /**
     * Deserializes a JSON to a list of Profile object.
     *
     * @param json The JSON to deserialize.
     * @return The JSON parsed to a list of Profile instances.
     * @throws UnknownFormatException Thrown if there was a problem while parsing the JSON.
     */
    public List<Profile> deserializeProfileList(JSONObject json) throws UnknownFormatException {
        try {
            if (json.has("data")) {
                JSONObject dataObject = json.getJSONObject("data");
                if (dataObject.has("profile_list")) {
                    JSONArray poolList = dataObject.getJSONArray("profile_list");
                    List<Profile> result = new ArrayList<>(poolList.length());
                    for (int i = 0; i < poolList.length(); ++i) {
                        JSONObject profileObject = poolList.getJSONObject(i);
                        result.add(deserializeProfile(profileObject));
                    }
                    return result;
                } else {
                    throw new UnknownFormatException("'profile_list' key inside 'data' missing!");
                }
            } else {
                throw new UnknownFormatException("'data' key missing!");
            }
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes a profile to JSON.
     *
     * @param profile The profile to serialize.
     * @return The profile in a JSON format.
     */
    public JSONObject serializeProfile(Profile profile) throws UnknownFormatException {
        JSONObject result = new JSONObject();
        try {
            result.put("display_name", profile.getDisplayName());
            result.put("profile_id", profile.getProfileId());
            result.put("two_factor", profile.getTwoFactor());
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes a profile JSON to an object instance.
     *
     * @param jsonObject The JSON to deserialize.
     * @return The profile as a POJO
     * @throws UnknownFormatException Thrown if the format was unknown.
     */
    public Profile deserializeProfile(JSONObject jsonObject) throws UnknownFormatException {
        try {
            String displayName = jsonObject.getString("display_name");
            String profileId = jsonObject.getString("profile_id");
            Boolean twoFactor = jsonObject.getBoolean("two_factor");
            return new Profile(displayName, profileId, twoFactor);
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes a JSON to an InstanceList instance.
     *
     * @param json The JSON to deserialize.
     * @return The JSON in the InstanceList POJO format.
     * @throws UnknownFormatException Thrown if there was a problem while parsing the JSON.
     */
    public InstanceList deserializeInstanceList(JSONObject json) throws UnknownFormatException {
        try {
            Integer version = json.getInt("version");
            if (version != 1) {
                throw new UnknownFormatException("Unknown version property: " + version);
            }
            JSONArray instanceArray = json.getJSONArray("instances");
            List<Instance> instances = new ArrayList<>();
            for (int i = 0; i < instanceArray.length(); ++i) {
                JSONObject instanceObject = instanceArray.getJSONObject(i);
                instances.add(deserializeInstance(instanceObject));
            }
            return new InstanceList(version, instances);
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes an instance to a JSON format.
     *
     * @param instance The instance to serialize.
     * @return The JSON object if the serialization was successful.
     * @throws UnknownFormatException Thrown if there was an error.
     */
    public JSONObject serializeInstance(Instance instance) throws UnknownFormatException {
        JSONObject result = new JSONObject();
        try {
            result.put("base_uri", instance.getBaseURI());
            result.put("display_name", instance.getDisplayName());
            result.put("logo_uri", instance.getLogoUri());
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
        return result;
    }

    /**
     * Deserializes an instance object from a JSON.
     *
     * @param jsonObject The JSON object to parse.
     * @return The instance as a POJO.
     * @throws UnknownFormatException Thrown when the format was not as expected.
     */
    public Instance deserializeInstance(JSONObject jsonObject) throws UnknownFormatException {
        try {
            String baseUri = jsonObject.getString("base_uri");
            String displayName = jsonObject.getString("display_name");
            String logoUri = null;
            if (jsonObject.has("logo_uri")) {
                logoUri = jsonObject.getString("logo_uri");
            }
            return new Instance(baseUri, displayName, logoUri);
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }

    }

    /**
     * Serializes an InstanceList object.
     *
     * @param instanceList The object to serialize.
     * @return The result in a JSON representation.
     * @throws UnknownFormatException Thrown if there was a problem when serializing.
     */
    public JSONObject serializeInstanceList(InstanceList instanceList) throws UnknownFormatException {
        try {
            JSONObject serialized = new JSONObject();
            serialized.put("version", instanceList.getVersion());
            JSONArray serializedInstances = new JSONArray();
            for (Instance instance : instanceList.getInstanceList()) {
                JSONObject serializedInstance = new JSONObject();
                serializedInstance.put("base_uri", instance.getBaseURI());
                serializedInstance.put("display_name", instance.getDisplayName());
                serializedInstance.put("logo_uri", instance.getLogoUri());
                serializedInstances.put(serializedInstance);
            }
            serialized.put("instances", serializedInstances);
            return serialized;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes a JSON object containing the discovered API endpoints.
     *
     * @param result The JSON object to deserialize
     * @return The discovered API object.
     * @throws UnknownFormatException Thrown if the JSON had an unknown format.
     */
    public DiscoveredAPI deserializeDiscoveredAPI(JSONObject result) throws UnknownFormatException {
        try {
            Integer version = result.getInt("version");
            if (version != 1) {
                throw new UnknownFormatException("Unknown version: " + version);
            }
            String authorizationEndpoint = result.getString("authorization_endpoint");
            if (authorizationEndpoint == null) {
                throw new UnknownFormatException("'authorization_endpoint' is missing!");
            }
            JSONObject apiObject = result.getJSONObject("api");
            String createConfigAPI = apiObject.getString("create_config");
            if (createConfigAPI == null) {
                throw new UnknownFormatException("'create_config' is missing!");
            }
            String profileListAPI = apiObject.getString("profile_list");
            if (profileListAPI == null) {
                throw new UnknownFormatException("'profile_list' is missing!");
            }
            String systemMessagesAPI = null;
            if (apiObject.has("system_messages")) {
                systemMessagesAPI = apiObject.getString("system_messages");
            }
            String userMessagesAPI = null;
            if (apiObject.has("user_messages")) {
                userMessagesAPI = apiObject.getString("user_messages");
            }
            return new DiscoveredAPI(version, authorizationEndpoint, createConfigAPI,
                    profileListAPI, systemMessagesAPI, userMessagesAPI);
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes a discovered API object.
     *
     * @param discoveredAPI The object to serialize to JSON.
     * @return The object as a JSON.
     * @throws UnknownFormatException Thrown if there was an error while parsing.
     */
    public JSONObject serializeDiscoveredAPI(DiscoveredAPI discoveredAPI) throws UnknownFormatException {
        JSONObject result = new JSONObject();
        try {
            result.put("version", discoveredAPI.getVersion());
            result.put("authorization_endpoint", discoveredAPI.getAuthorizationEndpoint());
            JSONObject apiObject = new JSONObject();
            apiObject.put("create_config", discoveredAPI.getCreateConfigAPI());
            apiObject.put("profile_list", discoveredAPI.getProfileListAPI());
            apiObject.put("user_messages", discoveredAPI.getUserMessagesAPI());
            apiObject.put("system_messages", discoveredAPI.getSystemMessagesAPI());
            result.put("api", apiObject);
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes a JSON with a list of messages into an ArrayList of message object.
     *
     * @param jsonObject The JSON to deserialize.
     * @return The message instances in a list.
     * @throws UnknownFormatException Thrown if there was a problem while parsing.
     */
    public List<Message> deserializeMessageList(JSONObject jsonObject) throws UnknownFormatException {
        try {
            JSONObject dataObject = jsonObject.getJSONObject("data");
            JSONArray messagesArray = dataObject.getJSONArray("messages");
            List<Message> result = new ArrayList<>();
            for (int i = 0; i < messagesArray.length(); ++i) {
                JSONObject messageObject = messagesArray.getJSONObject(i);
                String dateString = messageObject.getString("date");
                Date date = API_DATE_FORMAT.parse(dateString);
                String messageType = messageObject.getString("type");
                if ("maintenance".equals(messageType)) {
                    String startString = messageObject.getString("start");
                    Date startDate = API_DATE_FORMAT.parse(startString);
                    String endString = messageObject.getString("end");
                    Date endDate = API_DATE_FORMAT.parse(endString);
                    result.add(new Maintenance(date, startDate, endDate));
                } else if ("notification".equals(messageType)) {
                    String content = messageObject.getString("content");
                    result.add(new Notification(date, content));
                } else {
                    Log.w(TAG, "Unknown message type: " + messageType);
                }
            }
            return result;
        } catch (JSONException | ParseException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes a list of messages into a JSON format.
     *
     * @param messageList The list of messages to serialize.
     * @return The messages as a JSON object.
     * @throws UnknownFormatException Thrown if there was an error constructing the JSON.
     */
    public JSONObject serializeMessageList(List<Message> messageList) throws UnknownFormatException {
        try {
            JSONObject result = new JSONObject();
            JSONObject dataObject = new JSONObject();
            result.put("data", dataObject);
            JSONArray messagesArray = new JSONArray();
            dataObject.put("messages", messagesArray);
            for (Message message : messageList) {
                JSONObject messageObject = new JSONObject();
                messageObject.put("date", API_DATE_FORMAT.format(message.getDate()));
                if (message instanceof Maintenance) {
                    messageObject.put("start", API_DATE_FORMAT.format(((Maintenance)message).getStart()));
                    messageObject.put("end", API_DATE_FORMAT.format(((Maintenance)message).getEnd()));
                    messageObject.put("type", "maintenance");
                } else if (message instanceof Notification) {
                    messageObject.put("content", ((Notification)message).getContent());
                    messageObject.put("type", "notification");
                } else {
                    throw new RuntimeException("Unexpected message format!");
                }
                messagesArray.put(messageObject);
            }
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes a list of saved access tokens.
     *
     * @param savedTokenList The list with the saved access tokens.
     * @return The parsed list in a JSON format.
     * @throws UnknownFormatException Thrown if there was an unexpected error.
     */
    public JSONObject serializeSavedTokenList(List<SavedToken> savedTokenList) throws UnknownFormatException {
        try {
            JSONObject result = new JSONObject();
            JSONArray array = new JSONArray();
            for (SavedToken savedToken : savedTokenList) {
                JSONObject tokenJson = new JSONObject();
                tokenJson.put("instance", serializeInstance(savedToken.getInstance()));
                tokenJson.put("access_token", savedToken.getAccessToken());
                array.put(tokenJson);
            }
            result.put("data", array);
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes a JSON containing the list of the saved access tokens.
     *
     * @param jsonObject The JSON containing the information.
     * @return The list as a POJO.
     * @throws UnknownFormatException Thrown if there was an error while deserializing.
     */
    public List<SavedToken> deserializeSavedTokenList(JSONObject jsonObject) throws UnknownFormatException {
        try {
            List<SavedToken> result = new ArrayList<>();
            JSONArray dataArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < dataArray.length(); ++i) {
                JSONObject tokenObject = dataArray.getJSONObject(i);
                Instance instance = deserializeInstance(tokenObject.getJSONObject("instance"));
                String accessToken = tokenObject.getString("access_token");
                result.add(new SavedToken(instance, accessToken));
            }
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes a list of saved profiles.
     *
     * @param savedProfileList The list of saved profiles.
     * @return The list as a JSON.
     * @throws UnknownFormatException Thrown if there was an error while serializing.
     */
    public JSONObject serializeSavedProfileList(List<SavedProfile> savedProfileList) throws UnknownFormatException {
        try {
            JSONObject result = new JSONObject();
            JSONArray array = new JSONArray();
            for (SavedProfile savedProfile : savedProfileList) {
                JSONObject profileJson = new JSONObject();
                profileJson.put("provider", serializeInstance(savedProfile.getInstance()));
                profileJson.put("profile", serializeProfile(savedProfile.getProfile()));
                profileJson.put("profile_uuid", savedProfile.getProfileUUID());
                array.put(profileJson);
            }
            result.put("data", array);
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes a list of saved profiles.
     *
     * @param jsonObject The JSON to deserialize from.
     * @return The list of saved profiles as a POJO.
     * @throws UnknownFormatException Thrown if there was an error while deserializing.
     */
    public List<SavedProfile> deserializeSavedProfileList(JSONObject jsonObject) throws UnknownFormatException {
        try {
            List<SavedProfile> result = new ArrayList<>();
            JSONArray dataArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < dataArray.length(); ++i) {
                JSONObject profileObject = dataArray.getJSONObject(i);
                Instance instance = deserializeInstance(profileObject.getJSONObject("provider"));
                Profile profile = deserializeProfile(profileObject.getJSONObject("profile"));
                String profileUUID = profileObject.getString("profile_uuid");
                result.add(new SavedProfile(instance, profile, profileUUID));
            }
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes a TTL cache of discovered APIs.
     *
     * @param ttlCache The cache to serialize.
     * @return The cache in a JSON format.
     * @throws UnknownFormatException Thrown if there was an error while serializing.
     */
    public JSONObject serializeDiscoveredAPITTLCache(TTLCache<DiscoveredAPI> ttlCache) throws UnknownFormatException {
        try {
            JSONObject result = new JSONObject();
            long purgeAfterSeconds = ttlCache.getPurgeAfterSeconds();
            result.put("purge_after_seconds", purgeAfterSeconds);
            Map<String, Pair<Date, DiscoveredAPI>> originalData = ttlCache.getEntries();
            JSONArray array = new JSONArray();
            result.put("data", array);
            for (Map.Entry<String, Pair<Date, DiscoveredAPI>> entry : originalData.entrySet()) {
                JSONObject entity = new JSONObject();
                entity.put("entry_date", entry.getValue().first.getTime());
                entity.put("key", entry.getKey());
                entity.put("discovered_api", serializeDiscoveredAPI(entry.getValue().second));
                array.put(entity);
            }
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes a TTL cache of discovered APIs.
     *
     * @param jsonObject The JSON object to deserialize from.
     * @return The cache object.
     * @throws UnknownFormatException Thrown if there was an error while deserializing.
     */
    public TTLCache<DiscoveredAPI> deserializeDiscoveredAPITTLCache(JSONObject jsonObject) throws UnknownFormatException {
        try {
            Map<String, Pair<Date, DiscoveredAPI>> originalData = new HashMap<>();
            long purgeAfterSeconds = jsonObject.getLong("purge_after_seconds");
            JSONArray dataArray = jsonObject.getJSONArray("data");
            for (int i = 0; i < dataArray.length(); ++i) {
                JSONObject entity = dataArray.getJSONObject(i);
                Date entryDate = new Date(entity.getLong("entry_date"));
                String key = entity.getString("key");
                DiscoveredAPI discoveredAPI = deserializeDiscoveredAPI(entity.getJSONObject("discovered_api"));
                originalData.put(key, new Pair<>(entryDate, discoveredAPI));
            }
            return new TTLCache<>(originalData, purgeAfterSeconds);
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Deserializes the app settings from JSON to POJO.
     *
     * @param jsonObject The json containing the settings.
     * @return The settings as an object.
     * @throws UnknownFormatException Thrown if there was a problem while parsing the JSON.
     */
    public Settings deserializeAppSettings(JSONObject jsonObject) throws UnknownFormatException {
        try {
            boolean useCustomTabs = jsonObject.getBoolean("use_custom_tabs");
            boolean forceTcp = jsonObject.getBoolean("force_tcp");
            return new Settings(useCustomTabs, forceTcp);
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }

    /**
     * Serializes the app settings to JSON.
     *
     * @param settings The settings to serialize.
     * @return The app settings in a JSON format.
     * @throws UnknownFormatException Thrown if there was an error while deserializing.
     */
    public JSONObject serializeAppSettings(Settings settings) throws UnknownFormatException {
        JSONObject result = new JSONObject();
        try {
            result.put("use_custom_tabs", settings.useCustomTabs());
            result.put("force_tcp", settings.forceTcp());
            return result;
        } catch (JSONException ex) {
            throw new UnknownFormatException(ex);
        }
    }
}