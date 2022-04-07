package io.mosip.registration.clientmanager.service;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import io.mosip.registration.clientmanager.dto.CenterMachineDto;
import io.mosip.registration.clientmanager.dto.http.*;
import io.mosip.registration.clientmanager.dto.registration.GenericDto;
import io.mosip.registration.clientmanager.entity.MachineMaster;
import io.mosip.registration.clientmanager.entity.RegistrationCenter;
import io.mosip.registration.clientmanager.repository.*;
import io.mosip.registration.clientmanager.spi.MasterDataService;
import io.mosip.registration.clientmanager.spi.SyncRestService;
import io.mosip.registration.clientmanager.util.SyncRestUtil;
import io.mosip.registration.keymanager.dto.CryptoRequestDto;
import io.mosip.registration.keymanager.dto.CryptoResponseDto;
import io.mosip.registration.keymanager.repository.KeyStoreRepository;
import io.mosip.registration.keymanager.spi.ClientCryptoManagerService;
import io.mosip.registration.keymanager.util.CryptoUtil;
import org.json.JSONArray;
import org.json.JSONException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class MasterDataServiceImpl implements MasterDataService {

    private static final String TAG = MasterDataServiceImpl.class.getSimpleName();
    private static final String MASTER_DATA_LAST_UPDATED = "masterdata.lastupdated";
    public static final String REG_APP_ID = "REGISTRATION";
    public static final String KERNEL_APP_ID = "KERNEL";

    private Context context;
    private SyncRestService syncRestService;
    private ClientCryptoManagerService clientCryptoManagerService;
    private MachineRepository machineRepository;
    private RegistrationCenterRepository registrationCenterRepository;
    private ApplicantValidDocRepository applicantValidDocRepository;
    private DocumentTypeRepository documentTypeRepository;
    private TemplateRepository templateRepository;
    private DynamicFieldRepository dynamicFieldRepository;
    private KeyStoreRepository keyStoreRepository;
    private LocationRepository locationRepository;
    private GlobalParamRepository globalParamRepository;


    @Inject
    public MasterDataServiceImpl(Context context, SyncRestService syncRestService,
                                 ClientCryptoManagerService clientCryptoManagerService,
                                 MachineRepository machineRepository,
                                 RegistrationCenterRepository registrationCenterRepository,
                                 DocumentTypeRepository documentTypeRepository,
                                 ApplicantValidDocRepository applicantValidDocRepository,
                                 TemplateRepository templateRepository,
                                 DynamicFieldRepository dynamicFieldRepository,
                                 KeyStoreRepository keyStoreRepository,
                                 LocationRepository locationRepository,
                                 GlobalParamRepository globalParamRepository) {
        this.context = context;
        this.syncRestService = syncRestService;
        this.clientCryptoManagerService = clientCryptoManagerService;
        this.machineRepository = machineRepository;
        this.registrationCenterRepository = registrationCenterRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.applicantValidDocRepository = applicantValidDocRepository;
        this.templateRepository = templateRepository;
        this.dynamicFieldRepository = dynamicFieldRepository;
        this.keyStoreRepository = keyStoreRepository;
        this.locationRepository = locationRepository;
        this.globalParamRepository = globalParamRepository;
    }

    @Override
    public CenterMachineDto getRegistrationCenterMachineDetails() {
        CenterMachineDto centerMachineDto = null;
        MachineMaster machineMaster = this.machineRepository.getMachine(clientCryptoManagerService.getMachineName());
        if(machineMaster == null)
            return centerMachineDto;

        List<RegistrationCenter> centers = this.registrationCenterRepository.getRegistrationCenter(machineMaster.getRegCenterId());
        if(centers == null || centers.isEmpty())
            return centerMachineDto;

        centerMachineDto = new CenterMachineDto();
        centerMachineDto.setMachineId(machineMaster.getId());
        centerMachineDto.setMachineName(machineMaster.getName());
        centerMachineDto.setMachineStatus(machineMaster.getIsActive());
        centerMachineDto.setCenterId(centers.get(0).getId());
        centerMachineDto.setCenterStatus(centers.get(0).getIsActive());
        centerMachineDto.setMachineRefId(centerMachineDto.getCenterId()+"_"+centerMachineDto.getMachineId());
        centerMachineDto.setCenterNames(centers.stream().collect(Collectors.toMap(RegistrationCenter::getLangCode, RegistrationCenter::getName)));
        return centerMachineDto;
    }

    @Override
    public void manualSync() {
        try {
            syncMasterData();
            syncCertificate();
        } catch (Exception ex) {
            Log.e(TAG, "Data Sync failed", ex);
            Toast.makeText(context, "Data Sync failed", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void syncCertificate() {
        CenterMachineDto centerMachineDto = getRegistrationCenterMachineDetails();
        if(centerMachineDto == null)
            return;

        Call<ResponseWrapper<CertificateResponse>> call = syncRestService.getCertificate(REG_APP_ID,
                centerMachineDto.getMachineRefId());
        call.enqueue(new Callback<ResponseWrapper<CertificateResponse>>() {
            @Override
            public void onResponse(Call<ResponseWrapper<CertificateResponse>> call, Response<ResponseWrapper<CertificateResponse>> response) {
                if(response.isSuccessful()) {
                    ServiceError error = SyncRestUtil.getServiceError(response.body());
                    if (error == null) {
                        keyStoreRepository.saveKeyStore(centerMachineDto.getMachineRefId(), response.body().getResponse().getCertificate());
                        Toast.makeText(context, "Policy key Sync Completed", Toast.LENGTH_LONG).show();
                    } else
                        Toast.makeText(context, "Policy key Sync failed " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
                else
                    Toast.makeText(context, "Policy key Sync failed with status code : " + response.code(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Call<ResponseWrapper<CertificateResponse>> call, Throwable t) {
                Toast.makeText(context, "Policy key Sync failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void syncMasterData() throws Exception {
        CenterMachineDto centerMachineDto = getRegistrationCenterMachineDetails();

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("keyindex", this.clientCryptoManagerService.getClientKeyIndex());
        queryParams.put("version", "0.1");

        if(centerMachineDto != null)
            queryParams.put("regcenterId", centerMachineDto.getCenterId());

        /*String delta = this.globalParamRepository.getGlobalParamValue(MASTER_DATA_LAST_UPDATED);
        if(delta != null)
            queryParams.put("lastUpdated", delta);*/

        Call<ResponseWrapper<ClientSettingDto>> call = syncRestService.fetchMasterDate(queryParams);

        call.enqueue(new Callback<ResponseWrapper<ClientSettingDto>>() {
            @Override
            public void onResponse(Call<ResponseWrapper<ClientSettingDto>> call, Response<ResponseWrapper<ClientSettingDto>> response) {
                if(response.isSuccessful()) {
                    ServiceError error = SyncRestUtil.getServiceError(response.body());
                    if(error == null) {
                        saveMasterData(response.body().getResponse());
                        Toast.makeText(context, "Master Data Sync Completed", Toast.LENGTH_LONG).show();
                    }
                    else
                        Toast.makeText(context, "Master Data Sync failed " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
                else
                    Toast.makeText(context, "Master Data Sync failed with status code : " + response.code(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Call<ResponseWrapper<ClientSettingDto>> call, Throwable t) {
                Toast.makeText(context, "Master Sync failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveMasterData(ClientSettingDto clientSettingDto) {
        boolean foundErrors = false;
        for(MasterData masterData : clientSettingDto.getDataToSync()) {
            try {
                switch(masterData.getEntityType()) {
                    case "structured" :
                        saveStructuredData(masterData.getEntityName(), masterData.getData());
                        break;
                    case "dynamic" :
                        saveDynamicData(masterData.getData());
                }
            } catch (Throwable e) {
                foundErrors = true;
                Log.e(TAG, "Failed to parse the data", e);
            }
        }

        if(!foundErrors) {
            Log.i(TAG, "Masterdata lastSyncTime : " + clientSettingDto.getLastSyncTime());
            this.globalParamRepository.saveGlobalParam(MASTER_DATA_LAST_UPDATED, clientSettingDto.getLastSyncTime());
        }
    }

    private JSONArray getDecryptedDataList(String data) throws JSONException {
        CryptoRequestDto cryptoRequestDto = new CryptoRequestDto();
        cryptoRequestDto.setValue(data);
        CryptoResponseDto cryptoResponseDto = clientCryptoManagerService.decrypt(cryptoRequestDto);
        return new JSONArray(new String(CryptoUtil.base64decoder.decode(cryptoResponseDto.getValue())));
    }

    private void saveStructuredData(String entityName, String data) throws JSONException {
        switch (entityName) {
            case "Machine":
                JSONArray machines = getDecryptedDataList(data);
                machineRepository.saveMachineMaster(machines.getJSONObject(0));
                break;
            case "RegistrationCenter":
                JSONArray centers = getDecryptedDataList(data);
                for(int i =0 ;i < centers.length(); i++) {
                    registrationCenterRepository.saveRegistrationCenter(centers.getJSONObject(i));
                }
                break;
            case "DocumentType":
                JSONArray doctypes = getDecryptedDataList(data);
                for(int i =0 ;i < doctypes.length(); i++) {
                    documentTypeRepository.saveDocumentType(doctypes.getJSONObject(i));
                }
                break;
            case "ApplicantValidDocument":
                JSONArray appValidDocs = getDecryptedDataList(data);
                for(int i =0 ;i < appValidDocs.length(); i++) {
                    applicantValidDocRepository.saveApplicantValidDocument(appValidDocs.getJSONObject(i));
                }
                break;
            case "Template":
                JSONArray templates = getDecryptedDataList(data);
                for(int i =0 ;i < templates.length(); i++) {
                    templateRepository.saveTemplate(templates.getJSONObject(i));
                }
                break;
            case "Location":
                JSONArray locations = getDecryptedDataList(data);
                for(int i =0 ;i < locations.length(); i++) {
                    locationRepository.saveLocationData(locations.getJSONObject(i));
                }
                break;
        }
    }

    private void saveDynamicData(String data) throws JSONException {
        JSONArray list = getDecryptedDataList(data);
        for(int i =0 ;i < list.length(); i++) {
            dynamicFieldRepository.saveDynamicField(list.getJSONObject(i));
        }
    }

    /**
     * TODO - Currently stubbed to support dependent tasks
     */
    @Override
    public List<GenericDto> getAllLocationHierarchyLevels(String langCode) {
        List<GenericDto> list = new ArrayList<>();
        list.add(new GenericDto("1", "Country", langCode));
        list.add(new GenericDto("2", "Region", langCode));
        list.add(new GenericDto("3", "Province", langCode));
        list.add(new GenericDto("4", "City", langCode));
        list.add(new GenericDto("5", "Postal Code", langCode));
        return list;
    }


    @Override
    public List<String> getFieldValues(String fieldName, String langCode) {
        return dynamicFieldRepository.getDynamicValues(fieldName, langCode);
    }


    @Override
    public List<String> findLocationByParentHierarchyCode(String parentCode, String langCode) {
        return this.locationRepository.getLocations(parentCode, langCode);
    }

    @Override
    public List<String> findLocationByHierarchyLevel(int hierarchyLevel, String langCode) {
        return this.locationRepository.getLocationsBasedOnHierarchyLevel(hierarchyLevel, langCode);
    }

    @Override
    public List<String> getDocumentTypes(String categoryCode, String applicantType, String langCode) {
        return this.applicantValidDocRepository.getDocumentTypes(applicantType, categoryCode, langCode);
    }
}