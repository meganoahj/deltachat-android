package org.thoughtcrime.securesms.map;

import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.mapbox.geojson.Feature;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.maps.SupportMapFragment;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.geolocation.DcLocation;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static org.thoughtcrime.securesms.map.MapDataManager.MARKER_SELECTED;
import static org.thoughtcrime.securesms.map.MapDataManager.MESSAGE_ID;

public class MapActivity extends BaseActivity implements Observer {

    public static final String TAG = MapActivity.class.getSimpleName();
    public static final String CHAT_ID = "chat_id";
    public static final String MAP_TAG = "org.thoughtcrime.securesms.map";

    private DcLocation dcLocation;
    private MapDataManager mapDataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);
        int chatId = getIntent().getIntExtra(CHAT_ID, -1);
        if (chatId == -1) {
            finish();
            return;
        }

        dcLocation = DcLocation.getInstance();

        SupportMapFragment mapFragment;
        if (savedInstanceState == null) {
            final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            mapFragment = SupportMapFragment.newInstance();
            transaction.add(R.id.container, mapFragment, MAP_TAG);
            transaction.commit();
        } else {
            mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentByTag(MAP_TAG);
        }

        mapFragment.getMapAsync(mapboxMap -> mapboxMap.setStyle(Style.MAPBOX_STREETS, style -> {

            mapboxMap.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(dcLocation.getLastLocation().getLatitude(), dcLocation.getLastLocation().getLongitude()))
                    .zoom(9)
                    .build());
            mapboxMap.getUiSettings().setLogoEnabled(false);
            mapboxMap.getUiSettings().setAttributionEnabled(false);

            Style mapBoxStyle = mapboxMap.getStyle();
            if (mapBoxStyle == null) {
                return;
            }

            mapDataManager = new MapDataManager(this, mapBoxStyle, chatId, (latLngBounds) -> {
                mapboxMap.easeCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 50), 1000);
            });

            mapboxMap.addOnMapClickListener(point -> {
                final PointF pixel = mapboxMap.getProjection().toScreenLocation(point);
                Log.d(TAG, "on item clicked.");

                List<Feature> features = mapboxMap.queryRenderedFeatures(pixel, mapDataManager.getMarkerLayers());
                for (Feature feature : features) {
                    Log.d(TAG, "found feature: " + feature.toJson());
                    //show first feature that has meta data infos
                    if (feature.hasProperty(MARKER_SELECTED))  {
                        mapDataManager.setMarkerSelected(feature.id());
                        return true;
                    }
                }
                mapDataManager.unselectMarker();
                return false;
            });

            mapboxMap.addOnMapClickListener(point -> {
                final PointF pixel = mapboxMap.getProjection().toScreenLocation(point);
                Log.d(TAG, "on info window clicked.");

                List<Feature> features = mapboxMap.queryRenderedFeatures(pixel, mapDataManager.getInfoWindowLayers());
                for (Feature feature : features) {
                    Log.d(TAG, "found feature: " + feature.toJson());
                    if (feature.hasProperty(MARKER_SELECTED) && feature.getBooleanProperty(MARKER_SELECTED))  {
                        int messageId = feature.getNumberProperty(MESSAGE_ID).intValue();

                        int msgs[] = DcHelper.getContext(MapActivity.this).getChatMsgs(chatId, 0, 0);
                        int startingPosition = -1;
                        for(int i=0; i< msgs.length; i++ ) {
                            if(msgs[i] == messageId) {
                                startingPosition = msgs.length-1-i;
                                break;
                            }
                        }

                        Intent intent = new Intent(MapActivity.this, ConversationActivity.class);
                        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, chatId);
                        intent.putExtra(ConversationActivity.LAST_SEEN_EXTRA, 0);
                        intent.putExtra(ConversationActivity.STARTING_POSITION_EXTRA, startingPosition);
                        startActivity(intent);
                        return true;
                    }
                }
                return false;
            });

            if (BuildConfig.DEBUG) {
                mapboxMap.addOnMapLongClickListener(point -> {
                    new AlertDialog.Builder(MapActivity.this)
                            .setMessage(getString(R.string.menu_delete_locations))
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                ApplicationContext.getInstance(MapActivity.this).dcLocationManager.deleteAllLocations();
                            })
                            .setNegativeButton(R.string.no, null)
                            .show();
                    return true;
                });
            }
        }));

    }

    @Override
    protected void onResume() {
        super.onResume();
        DcLocation.getInstance().addObserver(this);
        if (mapDataManager != null) {
            mapDataManager.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        DcLocation.getInstance().deleteObserver(this);
        if (mapDataManager != null) {
            mapDataManager.onPause();
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof DcLocation) {
            this.dcLocation = (DcLocation) o;
            Log.d(TAG, "show marker on map: " +
                    dcLocation.getLastLocation().getLatitude() + ", " +
                    dcLocation.getLastLocation().getLongitude());
            //TODO: consider implementing a button -> center map to current location
        }
    }
}