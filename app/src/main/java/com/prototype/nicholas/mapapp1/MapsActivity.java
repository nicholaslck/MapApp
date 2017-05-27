package com.prototype.nicholas.mapapp1;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private GoogleMap mMap;
    protected ProgressDialog pDialog;
    protected AlertDialog aDialog;
    private Button button;
    private EditText destination;
    private ArrayList<LatLng> turns;
    private ArrayList<String> instructions;

    private String url;
    private ArrayList<GeoPoint> points;
    private ArrayList<LatLng> mapBound;
    private boolean isRouteFound;
    private boolean isRouteUpdated;
    private boolean isLocationUpdated;

    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    protected LocationRequest mLocationRequest;

    //App init
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        button = (Button) findViewById(R.id.button2);
        destination = (EditText) findViewById(R.id.editText);

        points = new ArrayList<GeoPoint>();
        mapBound = new ArrayList<LatLng>();
        turns = new ArrayList<LatLng>();
        instructions = new ArrayList<String>();

        isRouteFound = false;
        url = null;
        isRouteUpdated = false;
        isLocationUpdated = false;
//        String key = findViewById()

        //Build and connect GoogleApiClient to obtain the cuurent location
        createLocationRequest();
        buildGoogleApiClient();

        //Create listener for button
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("button listener", "button pressed with edit text = " + destination.getText());
                if (destination.getText().toString() != "" && mLastLocation != null) {
                    //Hide keyboard
                    View view = MapsActivity.this.getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }

                    //Process destination and location into URL
                    url = urlCreator(mLastLocation, destination.getText().toString());
                    isRouteUpdated = true;

                    //Find route and update map
                    new GetRouteInfo_And_UpdateMap().execute();
                }
            }
        });
    }

    /***************Connect with locational service***************/
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public synchronized void onConnected(Bundle connectionHint) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e("onConnected", "Permission denied");
            return;
        }
        //Get current location as starting point
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            Log.i("onConnected", "mLastLocation: " + mLastLocation.getLatitude() + " , " + mLastLocation.getLongitude());
        }
        startLocationUpdates();
        updateMap();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e("onConnectionSuspended", "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.e("onConnectionSuspended", "Connection suspended");
    }
    /*************************************************************/

    /***************Connect with locational request service***************/
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    //Renew url when Location updated
    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        isLocationUpdated = true;
        //mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        //updateUI();
        if(mLastLocation != null){
            //Update the location marker on map
            updateMap();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected() /*&& !mRequestingLocationUpdates*/) {
            startLocationUpdates();
        }
    }
    /*************************************************************/

    /*************** Connect with JSON parser ***************/
    //Parsing mLasttLocation and destination to url
    protected String urlCreator(Location mLastLocation, String destination){
        String encodedDes = null;
        try {

            encodedDes = URLEncoder.encode(destination, "UTF-8").replace("+", "%20");
        }catch(UnsupportedEncodingException ignored) {
            // Can be safely ignored because UTF-8 is always supported
        }

        String thisURL = "https://maps.googleapis.com/maps/api/directions/json?origin=";
        Log.e("mLastlocation toSting","" + mLastLocation.getLatitude() + " , " + mLastLocation.getLongitude());
        thisURL = thisURL + mLastLocation.getLatitude() + "," + mLastLocation.getLongitude() + "&destination=";
        thisURL = thisURL + encodedDes;
        thisURL = thisURL + "&key=AIzaSyABELfvRwLePn7bDo0ZexxRMqzQMQRpvUw";
        Log.e("urlCreator", thisURL);

        return thisURL;
    }

    // GeoPoints decoding algorithm
    private List<GeoPoint> decodePoly(String encoded) {
        List<GeoPoint> poly = new ArrayList<GeoPoint>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            GeoPoint p = new GeoPoint(((double) lat / 1E5), ((double) lng / 1E5));
            poly.add(p);
            //Log.e("decodePoly", "decoded poly: " + p.getLat() + " , " + p.getLng());
        }
        return poly;
    }

    // Asycn send request and decode GeoPoints
    private class GetRouteInfo_And_UpdateMap extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Showing progress dialog
            pDialog = new ProgressDialog(MapsActivity.this);
            pDialog.setMessage("Please wait...finding route...");
            pDialog.setCancelable(false);
            pDialog.show();
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            HttpHandler sh = new HttpHandler();

            // Making a request to url and getting response
            String jsonStr = sh.makeServiceCall(url);
            //Log.e("JSON from google","Response from url: " + jsonStr);

            if(jsonStr != null){
                try{
                    JSONObject jsonObj = new JSONObject(jsonStr);
                    String status = jsonObj.getString("status");
                    Log.i("JSON from google", "status: " + status);
                    if(status.equals("OK")){
                        points.clear();
                        mapBound.clear();
                        turns.clear();
                        instructions.clear();

                        // Getting JSON Array node
                        JSONArray routes = jsonObj.getJSONArray("routes");

                        //Loop over array [routes]
                        for(int i =0; i<1; i++){ //only consider the first route found
                            //Resolve the object
                            JSONObject thisRoute = routes.getJSONObject(i);

                            //Calculate the camera bounds of the this route
                            JSONObject bounds = thisRoute.getJSONObject("bounds");
                            JSONObject northeast = bounds.getJSONObject("northeast");
                            JSONObject southwest = bounds.getJSONObject("southwest");
                            LatLng boundNorthEast = new LatLng(northeast.getDouble("lat"),northeast.getDouble("lng"));
                            LatLng boundSouthWest = new LatLng(southwest.getDouble("lat"),southwest.getDouble("lng"));
                            mapBound.add(boundNorthEast);
                            mapBound.add(boundSouthWest);

                            //Get points for polyline drawing
                            JSONArray legs = thisRoute.getJSONArray("legs");
                            //Loop over array [legs]
                            for(int j=0; j< legs.length(); j++){
                                JSONObject thisLeg = legs.getJSONObject(j);
                                JSONArray steps = thisLeg.getJSONArray("steps");

                                //loop over array [steps]
                                for(int k=0; k < steps.length(); k++){
                                    JSONObject thisStep = steps.getJSONObject(k);

                                    //Retrieve html instructions
                                    JSONObject thisTurn = thisStep.getJSONObject("start_location");
                                    turns.add(new LatLng(thisTurn.getDouble("lat"), thisTurn.getDouble("lng")));

                                    String htmlInstruction = thisStep.getString("html_instructions");
                                    String instruction = Html.fromHtml(htmlInstruction).toString();
                                    //Log.e("JSON from google", "Instruction: " + instruction);
                                    instructions.add(instruction);

                                    //Retrieve the object {polyline}
                                    JSONObject polyline = thisStep.getJSONObject("polyline");

                                    //Get points info from {polyline}
                                    String thisPoints = polyline.getString("points");
                                    //Log.e("JSON from google", "Points: " + thisPoints);

                                    //Decode the retrived encoded points string
                                    List<GeoPoint> decodedPoints = decodePoly(thisPoints);

                                    //concat thisPoints into points
                                    for(int l=0;l<decodedPoints.size();l++)
                                        points.add(decodedPoints.get(l));
                                }
                            }
                            isRouteFound = true;

                        }
                    }else{
                        Log.e("JSON from google", "No route found");
                    }
                }catch (final JSONException e){
                    Log.e("GetRouteInfo", "Json parsing error: " + e.getMessage());
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            // Dismiss the progress dialog
            if (pDialog.isShowing())
                pDialog.dismiss();
            updateMap();
        }
    }

    /*************************************************************/

    /*************** Map fragment update ***************/

    //Select map fragment and call onMapReady
    public void updateMap(){
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if(mLastLocation == null){
            pDialog = new ProgressDialog(MapsActivity.this);
            pDialog.setMessage("Your location cannot be found! please restart the app.");
            pDialog.setCancelable(false);
            pDialog.show();
        }else{
            mMap.clear();
            LatLng start = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            mMap.addMarker(new MarkerOptions().position(start).title("You are here").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher_round)));

            if (isRouteFound && points.size()>0 && turns.size()>0 && instructions.size()>0 && mapBound.size()>1) { //given destination

                // Add a marker start & end position
//                LatLng start = new LatLng(points.get(0).getLat(), points.get(0).getLng());
//                mMap.addMarker(new MarkerOptions().position(start).title("start"));
                LatLng end = new LatLng(points.get(points.size() - 1).getLat(), points.get(points.size() - 1).getLng());
                mMap.addMarker(new MarkerOptions().position(end).title(destination.getText().toString()));

                //draw lines
                PolylineOptions line = new PolylineOptions();
                line.color(Color.RED); // blue color #0064FF
                line.width(10);
                for (int i = 0; i < points.size(); i++) {
                    LatLng latlng = new LatLng(points.get(i).getLat(), points.get(i).getLng());
                    //mMap.addMarker(new MarkerOptions().position(latlng).title("Intruction").snippet(instructions.get(i)));
                    line.add(latlng);
                }
                line.visible(true);
                mMap.addPolyline(line);

                for (int i=0; i<turns.size();i++){
                    int num = i+1;
                    mMap.addMarker(new MarkerOptions().position(turns.get(i)).title("Intruction " + num).snippet(instructions.get(i)).alpha(0.3f));
                }

                //Calculate map bound
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(mapBound.get(0));
                builder.include(mapBound.get(1));
                if(isRouteUpdated)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 30));

            } else { //no destination is given
                if(!isLocationUpdated){
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 17));
                }
            }
            isRouteUpdated = false;
        }
    }
    /*************************************************************/
}
