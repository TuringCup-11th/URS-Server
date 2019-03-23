package com.njust.csa.reg.service;

import com.njust.csa.reg.repository.docker.*;
import com.njust.csa.reg.repository.entities.*;
import com.njust.csa.reg.util.ActivityUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.*;

@Service
public class AdminService {
    private final UserRepo userRepo;
    private final TableStructureRepo tableStructureRepo;
    private final TableInfoRepo tableInfoRepo;
    private final ApplicantInfoRepo applicantInfoRepo;
    private final ApplicantInfoViewRepo applicantInfoViewRepo;
    private final ActivityUtil activityUtil;

    @Autowired
    public AdminService(UserRepo userRepo, TableStructureRepo tableStructureRepo,
                        TableInfoRepo tableInfoRepo, ApplicantInfoRepo applicantInfoRepo,
                        ApplicantInfoViewRepo applicantInfoViewRepo,
                        ActivityUtil activityUtil){
        this.userRepo = userRepo;
        this.tableStructureRepo = tableStructureRepo;
        this.tableInfoRepo = tableInfoRepo;
        this.applicantInfoRepo = applicantInfoRepo;
        this.applicantInfoViewRepo = applicantInfoViewRepo;
        this.activityUtil = activityUtil;
    }

    public boolean login(String username, String password){
        return userRepo.existsByNameAndPassword(username, DigestUtils.md5Hex("NJUST" + password + "CSA"));
    }

    @Transactional
    public long postActivity(String activityName, String publisherName, Timestamp startTime, Timestamp endTime, JSONArray items){
        long activityId;
        TableInfoEntity tableInfo = new TableInfoEntity();
        tableInfo.setTitle(activityName);
        Optional<UserEntity> publisherEntity = userRepo.findByName(publisherName);
        if(!publisherEntity.isPresent()) return -1;
        tableInfo.setPublisher(publisherEntity.get().getId());
        tableInfo.setStartTime(startTime);
        tableInfo.setEndTime(endTime);
        tableInfo.setStatus((byte)0);
//        if(startTime == null || startTime.before(new Timestamp(System.currentTimeMillis()))){
//            tableInfo.setStatus("open");
//        }
//        else{
//            tableInfo.setStatus("close");
//        }

        tableInfoRepo.save(tableInfo);
        activityId = tableInfo.getId();

        List<TableStructureEntity> entities = createActivityStructure(activityId, items, -1);

        for (TableStructureEntity entity : entities) {
            try{
                tableStructureRepo.save(entity);
            } catch (Exception e){
                e.printStackTrace();
                return -1;
            }
        }
        return activityId;
    }

    //获取所有用户的基本信息
    public String getUser(){
        JSONArray response = new JSONArray();
        Iterable<UserEntity> userEntityIterator = userRepo.findAll();
        for (UserEntity userEntity : userEntityIterator) {
            JSONObject user = new JSONObject();
            user.put("id", userEntity.getId());
            user.put("name", userEntity.getName());
            response.put(user);
        }
        return response.toString();
    }

    public String getActivities(){
        return activityUtil.getActivities(true).toString();
    }

    public boolean setActivityStatus(long id, byte status){
        Optional<TableInfoEntity> table = tableInfoRepo.findById(id);
        if(table.isPresent()){
            TableInfoEntity tableInfoEntity = table.get();
            tableInfoEntity.setStatus(status);
            tableInfoRepo.save(tableInfoEntity);
            return true;
        }
        return false;
    }

    public String getActivityStructure(long id){
        return activityUtil.generateActivityStructure(id, true).toString();
    }

    //删除相关活动
    public String deleteActivity(long id){
        Optional tableEntity = tableInfoRepo.findById(id);
        if(!tableEntity.isPresent()) return "未找到ID对应的报名！";

        tableInfoRepo.delete((TableInfoEntity)tableEntity.get());
        return "";
    }

    //获取单个活动的所有报名者
    public String getActivityApplicants(long tableId){
        Optional tableInfoEntity = tableInfoRepo.findById(tableId);
        if(!tableInfoEntity.isPresent()) return "未找到ID对应的报名！";

        JSONArray responseJson = new JSONArray();

        List<ApplicantInfoViewEntity> applicantInfoEntities = applicantInfoViewRepo.findAllByTableId(tableId);

        Map<Integer, Map<Long, String>> applicantMap = new HashMap<>();
        for (ApplicantInfoViewEntity applicantInfoEntity : applicantInfoEntities) {
            if(!applicantMap.containsKey(applicantInfoEntity.getApplicantNumber())){
                applicantMap.put(applicantInfoEntity.getApplicantNumber(), new HashMap<>());
            }
            applicantMap.get(applicantInfoEntity.getApplicantNumber())
                    .put(applicantInfoEntity.getStructureId(), applicantInfoEntity.getValue());
        }

        List<TableStructureEntity> mainItemEntities =
                tableStructureRepo.findAllByTableIdAndBelongsToOrderByIndexNumber(tableId, null);

        applicantMap.forEach((key, value) ->
                responseJson.put(generateApplicantInfoJson(applicantMap, key, mainItemEntities)));

        return responseJson.toString();
    }

    /* ======内部方法====== */

    // 构造新报名结构
    // 由于使用到事务，基于SpringAOP，故只能为public
    // 不允许外部调用
    @Transactional
    public List<TableStructureEntity> createActivityStructure(long activityId, JSONArray items, long belongsTo){
        byte index = 0;
        List<TableStructureEntity> entityList = new ArrayList<>();
        Iterator objects = items.iterator();
        for(;objects.hasNext();){
            JSONObject item = (JSONObject)objects.next();
            TableStructureEntity structureEntity = new TableStructureEntity();
            structureEntity.setTableId(activityId);
            structureEntity.setTitle(item.getString("name"));
            structureEntity.setExtension(item.getString("extension"));
            structureEntity.setType(item.getString("type"));
            structureEntity.setIsUnique(item.getBoolean("unique") ? (byte)1 : (byte)0);
            structureEntity.setIsRequired(item.getBoolean("require") ? (byte)1 : (byte)0);
            structureEntity.setDefaultValue(item.isNull("defaultValue") ? "" : item.getString("defaultValue"));
            structureEntity.setDescription(item.getString("description"));
            structureEntity.setTips(item.getString("tip"));
            structureEntity.setIndexNumber(index);

            if(!item.isNull("case")){
                Iterator cases = item.getJSONArray("case").iterator();
                StringBuilder casesString = new StringBuilder();
                for(;cases.hasNext();){
                    casesString.append(cases.next());
                    if(cases.hasNext()) casesString.append(",");
                }

                structureEntity.setCases(casesString.toString());
            }

            if(!item.isNull("range")){
                JSONArray range = item.getJSONArray("range");
                structureEntity.setRanges(range.get(0) + "," + range.get(1));
            }

            if(belongsTo != -1){
                structureEntity.setBelongsTo(belongsTo);
            }

            index++;

            entityList.add(structureEntity);

            if(structureEntity.getType().equals("group")){
                for (TableStructureEntity tableStructureEntity : entityList) {
                    tableStructureRepo.save(tableStructureEntity);
                }
                entityList.clear();
                entityList.addAll(createActivityStructure(activityId, item.getJSONArray("subItem"), structureEntity.getId()));
            }

        }

        return entityList;
    }

    private JSONObject generateApplicantInfoJson(Map<Integer, Map<Long, String>> applicantMap, int applicantNumber,
                                                 List<TableStructureEntity> tableStructureEntities){
        JSONObject result = new JSONObject();
        for (TableStructureEntity tableStructureEntity : tableStructureEntities) {
            if(tableStructureEntity.getType().equals("group")){
                List<TableStructureEntity> subItemEntities = tableStructureRepo.
                        findAllByTableIdAndBelongsToOrderByIndexNumber(
                                tableStructureEntity.getTableId(), tableStructureEntity.getId());
                result.put(tableStructureEntity.getTitle(),
                        generateApplicantInfoJson(applicantMap, applicantNumber, subItemEntities));
            }
            else{
                result.put(tableStructureEntity.getTitle(),
                        applicantMap.get(applicantNumber).get(tableStructureEntity.getId()));
            }
        }
        return result;
    }
}
