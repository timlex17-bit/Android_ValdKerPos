package com.valdker.pos.network;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

public abstract class VolleyMultipartRequest extends Request<NetworkResponse> {

    private final Response.Listener<NetworkResponse> mListener;
    private final Map<String, String> headers;

    private final String boundary = "apiclient-" + System.currentTimeMillis();
    private static final String LINE_FEED = "\r\n";

    public VolleyMultipartRequest(int method,
                                  String url,
                                  Map<String, String> headers,
                                  Response.Listener<NetworkResponse> listener,
                                  Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.mListener = listener;
        this.headers = headers;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers;
    }

    @Override
    public String getBodyContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    @Override
    protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
        return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(NetworkResponse response) {
        mListener.onResponse(response);
    }

    @Override
    public byte[] getBody() throws AuthFailureError {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
            Map<String, String> params = getParams();
            if (params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    addFormField(dos, entry.getKey(), entry.getValue());
                }
            }

            Map<String, DataPart> data = getByteData();
            if (data != null) {
                for (Map.Entry<String, DataPart> entry : data.entrySet()) {
                    addFilePart(dos, entry.getKey(), entry.getValue());
                }
            }

            dos.writeBytes("--" + boundary + "--" + LINE_FEED);

        } catch (IOException e) {
            throw new AuthFailureError("Multipart error");
        }

        return bos.toByteArray();
    }

    protected abstract Map<String, DataPart> getByteData() throws AuthFailureError;

    private void addFormField(DataOutputStream dos, String name, String value) throws IOException {
        dos.writeBytes("--" + boundary + LINE_FEED);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + LINE_FEED);
        dos.writeBytes(LINE_FEED);
        dos.writeBytes(value + LINE_FEED);
    }

    private void addFilePart(DataOutputStream dos, String fieldName, DataPart dataFile)
            throws IOException {

        dos.writeBytes("--" + boundary + LINE_FEED);
        dos.writeBytes("Content-Disposition: form-data; name=\"" +
                fieldName + "\"; filename=\"" + dataFile.getFileName() + "\"" + LINE_FEED);
        dos.writeBytes("Content-Type: " + dataFile.getType() + LINE_FEED);
        dos.writeBytes(LINE_FEED);

        dos.write(dataFile.getContent());

        dos.writeBytes(LINE_FEED);
    }

    public static class DataPart {
        private final String fileName;
        private final byte[] content;
        private final String type;

        public DataPart(String fileName, byte[] content, String type) {
            this.fileName = fileName;
            this.content = content;
            this.type = type;
        }

        public String getFileName() { return fileName; }
        public byte[] getContent() { return content; }
        public String getType() { return type; }
    }
}
