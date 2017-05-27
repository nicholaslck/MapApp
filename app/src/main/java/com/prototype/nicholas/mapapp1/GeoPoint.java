package com.prototype.nicholas.mapapp1;

/**
 * Created by nicholas on 3/5/2017.
 */

public class GeoPoint {
    private double lat;
    private double lng;

    public GeoPoint(){

    }

    public GeoPoint(double lat, double lng){
        this.lat = lat;
        this.lng = lng;
    }

    public double getLat(){
        return this.lat;
    }

    public double getLng(){
        return this.lng;
    }

}
