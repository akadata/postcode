package net.tevp.postcode;

import java.net.MalformedURLException;
import java.net.URL;
import java.io.IOException;
import java.io.InputStream;

import org.json.*;
import android.location.*;
import java.util.HashSet;
import android.os.Bundle;
import android.content.Context;
import android.util.Log;

public class PostcodeBackend implements LocationListener  {	
	public static final String TAG = "PostcodeBackend";
    static Geohash e = new Geohash();
	Location last = null;
    
	private static String grabURL(String url) throws PostcodeException
	{
		URL what;
		try {			
			what = new URL(url);
		} catch (MalformedURLException e1) {
			throw new PostcodeException("Malformed for"+url, e1);
		}
		InputStream data;
		int max = 1000, sofar=0;
		
		byte[] out = new byte[max];
		try {
			data = what.openStream();

			while (sofar<max)
			{
				int ret = data.read(out,sofar,max-sofar);
				if (ret == -1)
					break;
				sofar += ret;
			}
		} catch (IOException e1) {
			throw new PostcodeException("IOException during url grab", e1);
		}

		return new String(out, 0, sofar);
	}

	private static String getUKPostcodes(double lat, double lon) throws PostcodeException
	{
		String data = grabURL(String.format("http://www.uk-postcodes.com/latlng/%.8f,%.8f.json",lat,lon));
		try 
		{
			JSONObject js = new JSONObject(data);
			return js.getString("postcode");
		}
		catch (JSONException e) {
			throw new PostcodeException("json issue", e);
		}
	}

	private static String getWhatIsMyPostcode(double lat, double lon) throws PostcodeException
	{
		String s = e.encode(lat, lon);
		String data = grabURL("http://whatismypostcode.appspot.com/"+s);
		return data;
	}

	private static String getGeonames(double lat, double lon) throws PostcodeException
	{
		String data = grabURL(String.format("http://ws.geonames.org/findNearbyPostalCodesJSON?lat=%.8f&lng=%.8f&maxRows=1",lat,lon));
		try 
		{
			JSONObject js = new JSONObject(data);
			return js.getJSONArray("postalCodes").getJSONObject(0).getString("postalCode");
		}
		catch (JSONException e) {
			throw new PostcodeException("json issue", e);
		}
	}

	static String get(double lat, double lon) throws PostcodeException
	{
		PostcodeException old = null;
		int i=0;
		while(true)
		{
			try
			{
				switch (i)
				{
					case 0:
						return getWhatIsMyPostcode(lat,lon);
					case 1:
						return getGeonames(lat,lon);
					case 2:
						return getUKPostcodes(lat,lon);
					default:
						assert old != null;
						throw old;
				}
			}
			catch (PostcodeException pe)
			{
				old = pe;
				i++;
			}
		}
	}

	HashSet<PostcodeListener> pls = null;
	String lastPostcode = null;
	LocationManager lm;

    public void getPostcode(Context context, final PostcodeListener callback)
	{
    	getPostcode(context, callback, false);
	}

    public void getPostcode(Context context, final PostcodeListener callback, boolean mustBeNew)
	{
		Log.d(TAG, "Acquiring postcode from location");
		lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		Criteria c = new Criteria();
		Location l = null;
		for(String provider: lm.getProviders(false))
		{
			if (l == null && !mustBeNew)
			{
				l = lm.getLastKnownLocation(provider);
				if (((System.currentTimeMillis()-l.getTime())/1000.0) > 60) // > 1 minute old
				{
					Log.d(TAG, "Got old data from "+provider);
					l = null;
				}
			}
			if (l == null)
				lm.requestLocationUpdates(provider, 0, 0, this);
		}
		if (pls == null)
			pls = new HashSet<PostcodeListener>();
		pls.add(callback);
			
		if (l!= null)
		{
			final Location l2 = l;
			Thread t = new Thread() { public void run() {
				updatedLocation(l2);
			}};
			t.start();
		}
	}

	public void updatedLocation(Location l)
	{
		if (pls == null)
			return;
		lm.removeUpdates(this);
		Log.d(TAG, "Got an updated location");
		for (PostcodeListener pl: pls)
			pl.updatedLocation(l);
		try
		{
			if (last == null || lastPostcode == null || l.getLatitude()!=last.getLatitude() || l.getLongitude() != last.getLongitude())
				lastPostcode = PostcodeBackend.get(l.getLatitude(),l.getLongitude());
			last = l;
			Log.d(TAG, "Postcode is "+lastPostcode);
			Log.d(TAG, "Have "+Integer.toString(pls.size())+" postcode listeners");
			for (PostcodeListener pl: pls)
			{
				pl.postcodeChange(lastPostcode);
			}
			pls = null;
		}
		catch(PostcodeException pe)
		{
			Log.e(TAG, "Parse error during new postcode", pe);
		}
	}

	public void onLocationChanged(Location l){
		if (((System.currentTimeMillis()-l.getTime())/1000.0) > 60) // > 1 minute old
			return;
		updatedLocation(l);
	}

	public void onProviderDisabled(String provider) {}
	public void onProviderEnabled(String provider) {}
	public void onStatusChanged(String provider, int status, Bundle extras) {}
}

