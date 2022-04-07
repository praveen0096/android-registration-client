package io.mosip.registration.clientmanager.repository;

import io.mosip.registration.clientmanager.dao.LocationDao;
import io.mosip.registration.clientmanager.entity.Location;
import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;
import java.util.List;

public class LocationRepository {

    private LocationDao locationDao;

    @Inject
    public LocationRepository(LocationDao locationDao) {
        this.locationDao = locationDao;
    }

    public List<String> getLocations(String parentLocCode, String langCode) {
        if(parentLocCode == null) {
            return this.locationDao.findParentLocation(langCode);
        }
        return this.locationDao.findAllLocationByParentLocCode(parentLocCode, langCode);
    }

    public List<String> getLocationsBasedOnHierarchyLevel(int level, String langCode) {
        return this.locationDao.findAllLocationByHierarchyLevel(level, langCode);
    }

    public void saveLocationData(JSONObject locationJson) throws JSONException {
        Location location = new Location(locationJson.getString("code"),
                locationJson.getString("langCode"));
        location.setName(locationJson.getString("name"));
        location.setHierarchyLevel(locationJson.getInt("hierarchyLevel"));
        location.setHierarchyName(locationJson.getString("hierarchyName"));
        location.setIsActive(locationJson.getBoolean("isActive"));
        location.setIsDeleted(locationJson.getBoolean("isDeleted"));
        this.locationDao.insert(location);
    }
}