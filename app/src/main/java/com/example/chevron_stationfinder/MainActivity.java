package com.example.chevron_stationfinder;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.example.chevron_stationfinder.fragments.SearchAddressFragment;
import com.example.chevron_stationfinder.fragments.SearchFragment;
import com.example.chevron_stationfinder.fragments.SearchThatHasFragment;
import com.example.chevron_stationfinder.fragments.StationDetailsFragment;
import com.example.chevron_stationfinder.fragments.StationListFragment;
import com.example.chevron_stationfinder.interfaces.OnFragmentInteractionListener;
import com.example.chevron_stationfinder.interfaces.OnStationListReady;
import com.example.chevron_stationfinder.models.Prediction;
import com.example.chevron_stationfinder.models.Station;
import com.example.chevron_stationfinder.models.StationList;
import com.example.chevron_stationfinder.utils.APIUtils;
import com.example.chevron_stationfinder.utils.PlacesAPIUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity
        implements OnFragmentInteractionListener, OnMapReadyCallback, StationListFragment.OnListFragmentInteractionListener, SearchAddressFragment.OnListFragmentInteractionListener {

    private static final String KEY = "AIzaSyDcthbWZfYguqLFE3ubQiESnNuIcV7rFSM";
    private FusedLocationProviderClient mFusedLocationClient;
    private Location loc;
    private ArrayList<Station> stations;
    private GoogleMap gMap;
    private OnStationListReady listReady;
    private SharedPreferences savedFilters, cache;
    private Integer[] filters;
    private Map<String, String> cachedPredictions;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        stations = new ArrayList<>();
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        SearchFragment searchFragment = new SearchFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, searchFragment).addToBackStack("search").commit();

        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        cache = getSharedPreferences("cache", MODE_PRIVATE);
        savedFilters = getSharedPreferences("filters", MODE_PRIVATE);
        if (savedFilters.getInt("distance", 0) == 0) {
            SharedPreferences.Editor editor = savedFilters.edit();
            editor.putInt("distance", 35);
            editor.putInt("hasCarWash", 0);
            editor.putInt("hasExtraMile", 0);
            editor.putInt("hasDiesel", 0);
            editor.putInt("hasTapToPay", 0);
            editor.putInt("hasCarWash", 0);
            editor.putInt("hasGroceryRewards", 0);
            editor.putInt("hasStore", 0);
        }
        filters = new Integer[]{savedFilters.getInt("hasExtraMile", 0), savedFilters.getInt("hasGroceryRewards", 0), savedFilters.getInt("hasStore", 0), savedFilters.getInt("hasTapToPay", 0), savedFilters.getInt("hasCarWash", 0), savedFilters.getInt("hasDiesel", 0), savedFilters.getInt("distance", 35)};
    }


    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof StationListFragment) {
            listReady = (StationListFragment) fragment;
        }
    }


    @Override
    public void onMapReady(final GoogleMap mMap) {
        statusCheck();
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        loc = location;
                        Bundle extra = new Bundle();
                        extra.putString("address", "Current Location");
                        loc.setExtras(extra);
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(location.getLatitude(),
                                        location.getLongitude()), 13));
                        gMap.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(),
                                location.getLongitude())));
                    }
                });
        gMap = mMap;
        FrameLayout mapFrame = findViewById(R.id.map);
        View googleLogo = mapFrame.findViewWithTag("GoogleWatermark");
        RelativeLayout.LayoutParams glLayoutParams = (RelativeLayout.LayoutParams) googleLogo.getLayoutParams();
        glLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
        glLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
        glLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_START, 0);
        glLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        glLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE);
        googleLogo.setLayoutParams(glLayoutParams);
        gMap.setOnMarkerClickListener(marker -> {
            stations.iterator().forEachRemaining(station -> {
                if (marker.getPosition().latitude == Double.valueOf(station.lat) && marker.getPosition().longitude == Double.valueOf(station.lng)) {
                    listReady.scrollToStation(station);
                }
            });
            return false;
        });
    }


    public void statusCheck() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }

    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> startActivityForResult(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS), 1))
                .setNegativeButton("No", (dialog, id) -> dialog.cancel());
        final AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == 1) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                final LocationListener mLocationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(final Location location) {
                        loc = location;
                        Bundle extra = new Bundle();
                        extra.putString("address", "Current Location");
                        loc.setExtras(extra);
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(location.getLatitude(),
                                        location.getLongitude()), 13));
                        gMap.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(),
                                location.getLongitude())));
                    }

                    @Override
                    public void onStatusChanged(String s, int i, Bundle bundle) {

                    }

                    @Override
                    public void onProviderEnabled(String s) {

                    }

                    @Override
                    public void onProviderDisabled(String s) {

                    }
                };
                LocationManager mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000,
                        5, mLocationListener);
            }
        }
    }


    private void markMap(ArrayList<Station> stations) {
        gMap.clear();
        if (loc != null)
            gMap.addMarker(new MarkerOptions().position(new LatLng(loc.getLatitude(), loc.getLongitude())));
        for (Station station : stations) {
            gMap.addMarker(new MarkerOptions()
                    .position(new LatLng(Double.parseDouble(station.lat), Double.parseDouble(station.lng)))
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.techron_logo_lockup)));
        }
    }

    private void getNearbyStations(final Location location) {
        if (location != null) {
            Call<StationList> call = APIUtils.getStationService().getStations(getParams(location));
            call.enqueue(new Callback<StationList>() {
                @Override
                public void onResponse(@NonNull Call<StationList> call, @NonNull Response<StationList> response) {
                    if (response.isSuccessful()) {
                        if (response.body() != null) {
                            stations = response.body().stations;
                        }
                        markMap(stations);
                        listReady.onListReady(applyFilters(filters));
                        listReady.changeAddressText(location.getExtras().getString("address"));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<StationList> call, @NonNull Throwable t) {
                    Log.d("Error", t.getMessage());
                }
            });
        }
    }

    private Map<String, String> getParams(Location location) {
        Map<String, String> params = new HashMap<>();
        params.put("lat", String.valueOf(location.getLatitude()));
        params.put("lng", String.valueOf(location.getLongitude()));
        params.put("brand", "ChevronTexaco");
        params.put("radius", String.valueOf(35));
        return params;
    }

    @Override
    public void onFragmentInteraction(String TAG, Object o) {
        if (TAG.equals("SearchFragment")) {
            View view = (View) o;
            StationListFragment stationListFragment;
            switch (view.getId()) {
                case R.id.button: {
                    Log.d("msg", "Nearby presssed");
                    String address;
                    address = loc == null ? null : "Current Location";
                    stationListFragment = StationListFragment.newInstance(stations, address);
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.fragment_container, stationListFragment).commit();
                    transaction.addToBackStack(null);
                    getNearbyStations(loc);
                    break;
                }
                case R.id.button2: {
                    Log.d("msg", "near Address");
                    FragmentTransaction transaction;
                    cachedPredictions = (Map<String, String>) cache.getAll();
                    ArrayList<Prediction> predictions = new ArrayList<>();
                    cachedPredictions.forEach((s, s2) -> predictions.add(new Prediction(s2, s)));
                    FrameLayout fullFragment = findViewById(R.id.full_fragment_container);
                    fullFragment.setVisibility(View.VISIBLE);
                    SearchAddressFragment searchAddressFragment = SearchAddressFragment.newInstance(predictions);
                    transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.full_fragment_container, searchAddressFragment);
                    transaction.commit();
                    transaction.addToBackStack(null);
                    break;
                }
                case R.id.button3: {
                    Log.d("msg", "That has");
                    stationListFragment = StationListFragment.newInstance(stations, "Current Location");
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.fragment_container, stationListFragment).addToBackStack(null).commit();
                    SearchThatHasFragment thatHasFragment = SearchThatHasFragment.newInstance(this.filters);
                    transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.fragment_container, thatHasFragment).addToBackStack(null);
                    transaction.commit();
                    getNearbyStations(loc);
                    break;
                }

            }
        } else if (TAG.equals("ListFragment")) {
            SearchThatHasFragment thatHasFragment = SearchThatHasFragment.newInstance(this.filters);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.appear_anim, R.anim.disappear_anim, R.anim.appear_anim, R.anim.disappear_anim);
            transaction.replace(R.id.fragment_container, thatHasFragment).addToBackStack(null);
            transaction.commit();
        } else if (TAG.equals("DetailsFragment")) {
            getSupportFragmentManager().popBackStack();
            gMap.animateCamera((CameraUpdateFactory.zoomTo(13)));
        } else if (TAG.equals("ThatHas")) {
            if (o instanceof View) {
                View view = (View) o;
                switch (view.getId()) {
                    case R.id.closeBtn: {
                        Log.d("msg", "That has close pressed");
                        getSupportFragmentManager().popBackStack();
                    }
                }
            } else if (o instanceof Integer[]) {
                Integer[] filters = (Integer[]) o;
                this.filters = filters;
                SharedPreferences.Editor editor = savedFilters.edit();
                editor.putInt("distance", filters[6]);
                editor.putInt("hasExtraMile", filters[0]);
                editor.putInt("hasGroceryRewards", filters[1]);
                editor.putInt("hasStore", filters[2]);
                editor.putInt("hasTapToPay", filters[3]);
                editor.putInt("hasCarWash", filters[4]);
                editor.putInt("hasDiesel", filters[5]);
                editor.commit();
                getSupportFragmentManager().popBackStack();
                ArrayList<Station> filteredStations = applyFilters(filters);
                listReady.onListReady(filteredStations);
                markMap(filteredStations);

            }
        } else if (TAG.equals("SearchAddress")) {
            if (o instanceof View) {
                getNearbyStations(loc);
                getSupportFragmentManager().popBackStack();
                StationListFragment stationListFragment = StationListFragment.newInstance(stations, "Current Location");
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.fragment_container, stationListFragment).addToBackStack(null).commit();
            }
        }

    }

    public ArrayList<Station> applyFilters(Integer[] filters) {
        ArrayList<Station> filteredStations = new ArrayList<>(stations);
        filteredStations.removeIf(s -> Float.valueOf(s.getDistance()) > filters[6]);
        if (filters[0] == 1)
            filteredStations.removeIf(s -> Integer.valueOf(s.getExtramile()) == 0);
        if (filters[1] == 1)
            filteredStations.removeIf(s -> Integer.valueOf(s.getLoyalty()) == 0);
        if (filters[2] == 1)
            filteredStations.removeIf(s -> Integer.valueOf(s.getCstore()) == 0);
        if (filters[3] == 1)
            filteredStations.removeIf(s -> Integer.valueOf(s.getNfc()) == 0);
        if (filters[4] == 1)
            filteredStations.removeIf(s -> Integer.valueOf(s.getCarwash()) == 0);
        if (filters[5] == 1)
            filteredStations.removeIf(s -> Integer.valueOf(s.getDiesel()) == 0);
        return (filteredStations);
    }


    @Override
    public void onListFragmentInteraction(Station station, int flag) {
        if (flag == 1) {
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + station.lat + "," + station.lng);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        } else if (flag == 2) {
            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(Double.parseDouble(station.lat), Double.parseDouble(station.lng)), 15));
            StationDetailsFragment detailFragment = StationDetailsFragment.newInstance(station);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.appear_anim, R.anim.disappear_anim, R.anim.appear_anim, R.anim.disappear_anim);
            transaction.replace(R.id.fragment_container, detailFragment).commit();
            transaction.addToBackStack(null);
        } else if (flag == 3) {
            gMap.animateCamera((CameraUpdateFactory.newLatLngZoom(
                    new LatLng(Double.parseDouble(station.lat), Double.parseDouble(station.lng)), 13)));
        }
    }


    @Override
    public void onListFragmentInteraction(Prediction item) {
        Map<String, String> params = new HashMap<>();
        params.put("placeid", item.getPlace_id());
        params.put("key", KEY);
        if (!cachedPredictions.containsKey(item.getPlace_id())) {
            SharedPreferences.Editor editor = cache.edit();
            editor.putString(item.getPlace_id(), item.getDescription());
            editor.commit();
        }
        Call<JsonObject> call = PlacesAPIUtils.getPlacesService().getPlaceDetails(params);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    JsonObject object = response.body().getAsJsonObject("result").getAsJsonObject("geometry").getAsJsonObject("location");
                    Location location = new Location("location");
                    double lat, lng;
                    lat = object.get("lat").getAsDouble();
                    lng = object.get("lng").getAsDouble();
                    LatLng latLng = new LatLng(lat, lng);
                    location.setLatitude(lat);
                    location.setLongitude(lng);
                    Bundle extra = new Bundle();
                    extra.putString("address", item.getDescription());
                    location.setExtras(extra);
                    Log.d("result", object.toString());
                    gMap.clear();
                    gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            latLng, 13));
                    loc = location;
                    getSupportFragmentManager().popBackStack();
                    StationListFragment stationListFragment = StationListFragment.newInstance(stations, "Current Location");
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.fragment_container, stationListFragment).addToBackStack(null).commit();
                    getNearbyStations(location);
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {

            }
        });

    }

}