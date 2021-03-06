package jp.egg.android.request2.volley;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Pair;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jp.egg.android.util.DUtil;
import jp.egg.android.util.JUtil;
import jp.egg.android.util.Json;
import jp.egg.android.util.Log;
import jp.egg.android.util.ReflectionUtils;

/**
 * Created by chikara on 2014/07/10.
 */
public abstract class BaseVolleyRequest<I, O> extends Request<O> {

    public static final int REQUEST_TYPE_DEFAULT = 0;
    public static final int REQUEST_TYPE_JSON = 1;
    public static final String SET_COOKIE = "Set-Cookie";
    public static final String COOKIE = "Cookie";
    /**
     * Charset for request.
     */
    private static final String JSON_PROTOCOL_CHARSET = "utf-8";
    /**
     * Content type for request.
     */
    private static final String JSON_PROTOCOL_CONTENT_TYPE =
            String.format("application/json; charset=%s", JSON_PROTOCOL_CHARSET);
    private Context mContext;
    private Response.Listener<O> mResponseListener;
    private Response.ErrorListener mErrorListener;

    private String mFinalUrl = null;

    private Priority mPriority = Priority.NORMAL;
    private Class<O> mBackedOutputType;
    private String mDeNormalizedUrl;

    private int mRequestType = REQUEST_TYPE_DEFAULT;


    protected BaseVolleyRequest(Context context, int method, String url) {
        this(context, method, url, null, null);
    }

    protected BaseVolleyRequest(Context context, int method, String url, Response.Listener<O> successListener, Response.ErrorListener errorListener) {
        super(method,
                url,
                null);
        mContext = context.getApplicationContext();
        mDeNormalizedUrl = url;
        mBackedOutputType = findGenericOutputTypes(
                findTargetClass(JUtil.getClass(BaseVolleyRequest.this)));

        setListeners(successListener, errorListener);
    }

    /**
     * Set-Cookieの値をクッキーのキーと値に
     *
     * @param cookie
     * @return
     */
    protected static Pair<String, String> parseSetCookieToNameValuePair(String cookie) {
        if (TextUtils.isEmpty(cookie)) {
            return null;
        }
        String[] arr = cookie.split(";", 0);
        if (arr.length == 0) {
            return null;
        }
        String[] pair = arr[0].trim().split("=", 2);
        if (pair.length != 2) {
            return null;
        }
        return new Pair<String, String>(pair[0], pair[1]);
    }

    @NonNull
    private Class findGenericOutputTypes(Class clazz) {
        Type genericSuperClass = clazz.getGenericSuperclass();
        if (genericSuperClass instanceof ParameterizedType) {
            Type[] types = ((ParameterizedType) genericSuperClass).getActualTypeArguments();
            if (types[1] instanceof Class) {
                return (Class) types[1];
            } else if (types[1] instanceof GenericArrayType) {
                GenericArrayType genericArrayType = (GenericArrayType) types[1];
                Class c1 = (Class) genericArrayType.getGenericComponentType();
                Class c2;
                try {
                    String cn = "[L" + c1.getName() + ";";
                    c2 = Class.forName(cn);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    throw new IllegalArgumentException("can not use type " + types[1]);
                }
                return c2;
            } else {
                throw new IllegalArgumentException("can not use type " + types[1]);
            }
        } else {
            throw new IllegalArgumentException("can not use type " + genericSuperClass);
        }
    }

    @NonNull
    private Class findTargetClass(Class clazz) {
        Class c = clazz;
        Class parameterizedTypeClass = null;
        while (c != null && c != BaseVolleyRequest.class) {
            Type genericSuperclass = c.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
                Type[] types = parameterizedType.getActualTypeArguments();
                if (types != null && types.length == 2) {
                    if (types[1] instanceof Class || types[1] instanceof GenericArrayType) {
                        parameterizedTypeClass = c;
                    }
                }
            }
            Class superClass = c.getSuperclass();
            c = superClass;
        }
        return parameterizedTypeClass;
    }

    protected Context getContext() {
        return mContext;
    }

    protected Class<O> getBackedOutputType() {
        return mBackedOutputType;
    }

    public void setListeners(Response.Listener<O> successListener, Response.ErrorListener errorListener) {
        setSuccessListener(successListener);
        setErrorListener(errorListener);
    }

    public void setSuccessListener(Response.Listener<O> successListener) {
        mResponseListener = successListener;
    }

    public void setErrorListener(Response.ErrorListener errorListener) {
        mErrorListener = errorListener;
    }

    protected void setRequestType(int requestType) {
        mRequestType = requestType;
    }

    protected abstract I getInput();

    @Override
    public String getUrl() {
        if (mFinalUrl != null) return mFinalUrl;

        int method = getMethod();
        if (method == Method.GET) {
            String baseUrl = super.getUrl();
            try {
                Uri.Builder builder = Uri.parse(baseUrl).buildUpon();
                List<Pair<String, String>> params2 = getParams2();
                for (Pair<String, String> e : params2) {
                    if (e.second != null) {
                        builder.appendQueryParameter(e.first, e.second);
                    }
                }
                return mFinalUrl = builder.toString();
            } catch (Exception authFailureError) {
                Log.e("request", "authFailureError url=" + baseUrl, authFailureError);
                return mFinalUrl = null;
            }
        } else {
            return mFinalUrl = super.getUrl();
        }
    }

    protected void convertInputToParams(List<Pair<String, String>> params, Field field, Object value) {
        Class type = field.getType();
        String key = field.getName();
        if (value == null) {
            // なし
        } else if (type.isArray()) {
            Object[] arr = (Object[]) value;
            for (int i = 0; i < arr.length; i++) {
                params.add(Pair.create(key, arr[i].toString()));
            }
        } else {
            params.add(Pair.create(key, value.toString()));
        }
    }

    protected List<Pair<String, String>> getParams2() {
        I in = getInput();

        List<Pair<String, String>> params2 = new LinkedList<Pair<String, String>>();
        Map<Field, Object> values = ReflectionUtils.getDeclaredFieldValues(
                in,
                new ReflectionUtils.FieldFilter() {
                    @Override
                    public boolean accept(Field field) {
                        int modifiers = field.getModifiers();
                        if (Modifier.isStatic(modifiers)) return false;
                        if (Modifier.isPrivate(modifiers)) return false;
                        if (Modifier.isProtected(modifiers)) return false;
                        if (field.getName().contains("$")) return false;
                        return true;
                    }
                },
                null
        );

        for (Map.Entry<Field, Object> e : values.entrySet()) {
            Field field = e.getKey();
            Object value = e.getValue();
            convertInputToParams(params2, field, value);
        }

        if (Log.isDebug()) {
            Log.d("request-input", "" + mDeNormalizedUrl + " params => " + DUtil.toStringPairList(params2, false));
        }
        return params2;
    }

    @Override
    @Deprecated
    protected final Map<String, String> getParams() throws AuthFailureError {
        throw new RuntimeException("not use!!!!!");
    }

    @Override
    @Deprecated
    public final byte[] getPostBody() throws AuthFailureError {
        throw new RuntimeException("not use!!!!!");
    }

    /**
     * Returns the raw POST or PUT body to be sent.
     *
     * @throws AuthFailureError in the event of auth failure
     */
    @Override
    public byte[] getBody() throws AuthFailureError {
        switch (mRequestType) {
            case REQUEST_TYPE_DEFAULT: {
                List<Pair<String, String>> params = getParams2();
                if (params != null && params.size() > 0) {
                    return encodeParameters(params, getParamsEncoding());
                }
                return null;
            }
            case REQUEST_TYPE_JSON: {
                return getBodyForJson();
            }
            default: {
                return null;
            }
        }
    }

    public String getBodyContentType() {
        switch (mRequestType) {
            case REQUEST_TYPE_DEFAULT:
                return super.getBodyContentType();
            case REQUEST_TYPE_JSON:
                return getBodyContentTypeForJson();
            default:
                return null;
        }
    }

    /**
     * JSONでのリクエスト用のリクエストボディ
     * REQUEST_TYPE_JSONのときのみ
     *
     * @return json string
     */
    private byte[] getBodyForJson() {
        String requestBody = getParamsJson();
        try {
            return requestBody == null ? null : requestBody.getBytes(JSON_PROTOCOL_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s",
                    requestBody, JSON_PROTOCOL_CHARSET);
            return null;
        }
    }

    /**
     * JSONでのリクエスよ用のコンテントタイプ
     *
     * @return
     */
    private String getBodyContentTypeForJson() {
        return JSON_PROTOCOL_CONTENT_TYPE;
    }

    /**
     * 入力されたInputをもとに、リクエスト用のJSONを返す
     *
     * @return
     */
    protected String getParamsJson() {
        I in = getInput();

        JsonNode jsonNode = Json.toJson(in);
        String string = Json.stringify(jsonNode);

        if (Log.isDebug()) {
            Log.d("request-input", "" + mDeNormalizedUrl + " params => " + string);
        }
        return string;
    }

    /**
     * Converts <code>params</code> into an application/x-www-form-urlencoded encoded string.
     */
    private byte[] encodeParameters(List<Pair<String, String>> params, String paramsEncoding) {
        StringBuilder encodedParams = new StringBuilder();
        try {
            for (Pair<String, String> entry : params) {
                encodedParams.append(URLEncoder.encode(entry.first, paramsEncoding));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.second, paramsEncoding));
                encodedParams.append('&');
            }
            return encodedParams.toString().getBytes(paramsEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("Encoding not supported: " + paramsEncoding, uee);
        }
    }

    protected abstract O getOutput(JsonNode jn);

    protected final void parseCookie(NetworkResponse response) {

        List<Pair<String, String>> headers = response.headers;
        List<String> result = new ArrayList<String>();
        for (Pair<String, String> header : headers) {
            if (SET_COOKIE.equalsIgnoreCase(header.first)) {
                result.add(header.second);
            }
        }
        if (result.size() > 0) {
            onReceivedCookie(result);
        }
    }

    protected void onReceivedCookie(List<String> cookies) {

    }

    @Override
    protected Response<O> parseNetworkResponse(NetworkResponse response) {

        parseCookie(response);

        String data = parseToString(response);

        JsonNode node;
        try {
            node = Json.parse(data);
        } catch (Exception ex) {
            node = null;
        }

        if (Log.isDebug()) {
            if (node != null) {
                Log.d("output", "" + mDeNormalizedUrl + " > " + Json.stringify(node));
            } else {
                Log.d("output", "" + mDeNormalizedUrl + " > " + data);
            }
        }

        if (node != null) {
            O bean = getOutput(node);
            return Response.success(bean, HttpHeaderParser.parseCacheHeaders(response));
        } else {
            return null;
        }
    }

    protected String parseToString(NetworkResponse response) {
        String data = null;
        try {
            data = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return data;
    }

    @Override
    protected void deliverResponse(O response) {
        if (mResponseListener != null) {
            mResponseListener.onResponse(response);
        }
    }

    @Override
    public void deliverError(VolleyError error) {
        if (mErrorListener != null) {
            mErrorListener.onErrorResponse(error);
        }
    }

    protected List<Pair<String, String>> getCookies() {
        List<Pair<String, String>> strCookies = new ArrayList<Pair<String, String>>();
        return strCookies;
    }

    private String makeCookieHeader(@NonNull List<Pair<String, String>> cookies) {
        StringBuilder builder = new StringBuilder();
        for (Pair<String, String> e : cookies) {
            if (builder.length() != 0) {
                builder.append("; ");
            }
            builder.append(e.first).append("=").append(e.second);
        }
        return builder.toString();
    }

    @Override
    public List<Pair<String, String>> getHeaders() throws AuthFailureError {
        List<Pair<String, String>> headers = new ArrayList<>(super.getHeaders());

        List<Pair<String, String>> cookies = getCookies();
        if (cookies != null && cookies.size() > 0) {
            String cookiesHeaderValue = makeCookieHeader(cookies);
            headers.add(new Pair<String, String>(COOKIE, cookiesHeaderValue));
            if (Log.isDebug()) {
                Log.d("header", "send cookie header, " + cookiesHeaderValue);
            }
        }

        return headers;
    }

    @Override
    public Priority getPriority() {
        return mPriority;
    }

    public void setPriority(Priority priority) {
        mPriority = priority;
    }


}
