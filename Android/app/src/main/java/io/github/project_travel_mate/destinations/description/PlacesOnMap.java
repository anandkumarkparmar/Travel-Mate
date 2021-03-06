package io.github.project_travel_mate.destinations.description;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lucasr.twowayview.TwoWayView;

import java.io.IOException;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.github.project_travel_mate.R;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import utils.GPSTracker;

import static utils.Constants.API_LINK;
import static utils.Constants.EXTRA_MESSAGE_ID;
import static utils.Constants.EXTRA_MESSAGE_LATITUDE;
import static utils.Constants.EXTRA_MESSAGE_LONGITUDE;
import static utils.Constants.EXTRA_MESSAGE_NAME;
import static utils.Constants.EXTRA_MESSAGE_TYPE;

public class PlacesOnMap extends AppCompatActivity implements OnMapReadyCallback {

    @BindView(R.id.lv)
    TwoWayView lv;

    private String deslon;
    private String deslat;

    private ProgressDialog progressDialog;
    private int mode;
    private int icon;
    private GoogleMap googleMap;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_places_on_map);

        ButterKnife.bind(this);

        Intent intent   = getIntent();
        String name     = intent.getStringExtra(EXTRA_MESSAGE_NAME);
        String id       = intent.getStringExtra(EXTRA_MESSAGE_ID);
        String type     = intent.getStringExtra(EXTRA_MESSAGE_TYPE);
        mHandler        = new Handler(Looper.getMainLooper());

        setTitle(name);

        switch (type) {
            case "restaurant":
                mode = 0;
                icon = R.drawable.restaurant;
                break;
            case "hangout":
                mode = 1;
                icon = R.drawable.hangout;
                break;
            case "monument":
                mode = 2;
                icon = R.drawable.monuments;
                break;
            default:
                mode = 4;
                icon = R.drawable.shopping;
                break;
        }

        deslat = intent.getStringExtra(EXTRA_MESSAGE_LATITUDE);
        deslon = intent.getStringExtra(EXTRA_MESSAGE_LONGITUDE);

        getPlaces();

        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        setTitle("Places");
        Objects.requireNonNull(getSupportActionBar()).setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }

    private void showMarker(Double locationLat, Double locationLong, String locationName) {
        LatLng coord = new LatLng(locationLat, locationLong);
        if (ContextCompat.checkSelfPermission(PlacesOnMap.this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(true);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coord, 14));

                MarkerOptions temp = new MarkerOptions();
                MarkerOptions markerOptions = temp
                        .title(locationName)
                        .position(coord)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_pin_drop_black_24dp));
                googleMap.addMarker(markerOptions);
            }
        }
    }

    private void getPlaces() {

        progressDialog = new ProgressDialog(PlacesOnMap.this);
        progressDialog.setMessage("Fetching data, Please wait...");
        progressDialog.setIndeterminate(true);
        progressDialog.show();

        // to fetch city names
        String uri = API_LINK + "places-api.php?lat=" + deslat + "&lng=" + deslon + "&mode=" + mode;
        Log.v("executing", "URI : " + uri );

        //Set up client
        OkHttpClient client = new OkHttpClient();
        //Execute request
        Request request = new Request.Builder()
                .url(uri)
                .build();
        //Setup callback
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.v("Request Failed", "Message : " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {

                final String res = Objects.requireNonNull(response.body()).string();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject feed = new JSONObject(res);

                            JSONArray feedItems = feed.getJSONArray("results");
                            Log.v("response", feedItems.toString());


                            lv.setAdapter(new CityInfoAdapter(PlacesOnMap.this, feedItems, icon));

                            progressDialog.dismiss();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.e("ERROR : ", e.getMessage());
                        }
                    }
                });

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {

        googleMap = map;

        GPSTracker tracker = new GPSTracker(this);
        if (!tracker.canGetLocation()) {
            tracker.showSettingsAlert();
        } else {
            String curlat = Double.toString(tracker.getLatitude());
            String curlon = Double.toString(tracker.getLongitude());
            if (curlat.equals("0.0")) {
                curlat = "28.5952242";
                curlon = "77.1656782";
            }
            LatLng coordinate = new LatLng(Double.parseDouble(curlat), Double.parseDouble(curlon));
            CameraUpdate yourLocation = CameraUpdateFactory.newLatLngZoom(coordinate, 14);
            map.animateCamera(yourLocation);
        }
    }

    class CityInfoAdapter extends BaseAdapter {

        final Context context;
        final JSONArray FeedItems;
        final int rd;
        LinearLayout b2;
        private final LayoutInflater inflater;

        CityInfoAdapter(Context context, JSONArray feedItems, int r) {
            this.context = context;
            this.FeedItems = feedItems;
            rd = r;
            inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return FeedItems.length();
        }

        @Override
        public Object getItem(int position) {
            // TODO Auto-generated method stub
            try {
                return FeedItems.getJSONObject(position);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View vi = convertView;
            if (vi == null)
                vi = inflater.inflate(R.layout.city_infoitem, parent, false);

            TextView title = vi.findViewById(R.id.item_name);
            TextView description = vi.findViewById(R.id.item_address);
            LinearLayout onmap = vi.findViewById(R.id.map);
            b2 = vi.findViewById(R.id.b2);


            try {
                title.setText(FeedItems.getJSONObject(position).getString("name"));
                description.setText(FeedItems.getJSONObject(position).getString("address"));
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("eroro", e.getMessage() + " ");
            }

            ImageView iv = vi.findViewById(R.id.image);
            iv.setImageResource(rd);

            onmap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    Intent browserIntent;
                    try {
                        browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps?q=" +
                                FeedItems.getJSONObject(position).getString("name") +
                                "+(name)+@" +
                                FeedItems.getJSONObject(position).getString("lat") +
                                "," +
                                FeedItems.getJSONObject(position).getString("lng")
                        ));
                        context.startActivity(browserIntent);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            });

            b2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent browserIntent;
                    browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.co.in/"));
                    context.startActivity(browserIntent);
                }
            });

            vi.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    googleMap.clear();
                    try {
                        showMarker(Double.parseDouble(FeedItems.getJSONObject(position).getString("lat")),
                                Double.parseDouble(FeedItems.getJSONObject(position).getString("lng")),
                                FeedItems.getJSONObject(position).getString("name"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            return vi;
        }
    }
}
