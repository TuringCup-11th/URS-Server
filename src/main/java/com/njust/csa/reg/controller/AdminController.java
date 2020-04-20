package com.njust.csa.reg.controller;

import com.njust.csa.reg.model.dto.CheckAuditDTO;
import com.njust.csa.reg.model.dto.CspAuditListDTO;
import com.njust.csa.reg.model.dto.CspAuditStatusDTO;
import com.njust.csa.reg.model.dto.CspFreeApplicantsDTO;
import com.njust.csa.reg.model.dto.CspScoreUploadDTO;
import com.njust.csa.reg.service.AdminService;
import com.njust.csa.reg.service.CspService;
import com.njust.csa.reg.util.AuditResult;
import com.njust.csa.reg.util.AuditStatus;
import com.njust.csa.reg.util.FailureException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

@RestController
public class AdminController {

    private static final Map<String, String> sessionList = new HashMap<>();

    private final AdminService adminService;

    private final CspService cspService;

    @Autowired
    public AdminController(AdminService adminService, CspService cspService) {
        this.adminService = adminService;
        this.cspService = cspService;
    }

    //    //重定向后的请求处理
    //    @RequestMapping(value = "/page/login", method = RequestMethod.GET)
    //    public String getIndex(HttpServletRequest request){
    //        request.getSession().setAttribute("isRedirect", true); //添加session信息，避免拦截器重复拦截
    //        return "/page/index.html";
    //    }
    //
    //    //处理管理端的所有静态请求
    //    @RequestMapping(value = "/page/activity/*", method = RequestMethod.GET)
    //    public String checkIndex(HttpSession session){
    //        //如果登录，则跳回主页面
    //        if(checkUser(session)){
    //            return "/page/index.html";
    //        }
    //        //如果访问静态请求时没有登录，则重定向
    //        return "redirect:/page/login";
    //    }

    //管理端登录
    @ResponseBody
    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public ResponseEntity adminLogin(@RequestBody String jsonString, HttpSession session) {
        JSONObject json = new JSONObject(jsonString);
        String username = json.getString("username");
        String password = json.getString("password");
        if (adminService.login(username, password)) {
            sessionList.merge(username, session.getId(), (key, value) -> value = session.getId());
            session.setAttribute("username", username);
            return new ResponseEntity(HttpStatus.OK);
        } else {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }
    }

    //管理端登出
    @ResponseBody
    @RequestMapping(value = "/logout", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ResponseEntity adminLogout(HttpSession session) {
        if (session.getAttribute("username") != null) {
            sessionList.remove(session.getAttribute("username").toString());
            session.removeAttribute("username");
        }
        return new ResponseEntity(HttpStatus.OK);
    }

    // 创建报名
    @ResponseBody
    @RequestMapping(value = "/admin/activity", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> postActivity(@RequestBody String jsonString, HttpSession session) {

        if (checkUser(session)) {
            JSONObject json = new JSONObject(jsonString);
            String activityName;
            Timestamp startTime;
            Timestamp endTime;
            JSONArray items;
            try {
                activityName = json.getString("name");
                startTime = json.isNull("startTime") ? null : Timestamp.valueOf(json.getString("startTime"));
                endTime = json.isNull("endTime") ? null : Timestamp.valueOf(json.getString("endTime"));
                items = json.getJSONArray("items");

                long activityId = adminService.postActivity(activityName, session.getAttribute("username").toString(), startTime, endTime, items);
                if (activityId != -1) {
                    JSONObject response = new JSONObject();
                    response.put("id", activityId);
                    return new ResponseEntity<>(response.toString(), HttpStatus.OK);
                } else {
                    return new ResponseEntity<>("", HttpStatus.NOT_ACCEPTABLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return new ResponseEntity<>("", HttpStatus.NOT_ACCEPTABLE);
            }
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    //获取用户列表，用于登录
    @ResponseBody
    @RequestMapping(value = "/user", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> getUsers() {
        return new ResponseEntity<>(adminService.getUser(), HttpStatus.OK);
    }

    //获取所有活动
    @ResponseBody
    @RequestMapping(value = "/admin/activity", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> adminGetActivity(HttpSession session) {
        if (checkUser(session)) {
            return new ResponseEntity<>(adminService.getActivities(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    //设置活动状态
    @ResponseBody
    @RequestMapping(value = "/admin/activity/{id}/status", method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    public ResponseEntity setActivityStatus(@PathVariable long id, @RequestBody String jsonString, HttpSession session) {
        if (checkUser(session)) {
            byte status = (byte) new JSONObject(jsonString).getInt("status");
            if (adminService.setActivityStatus(id, status)) {
                return new ResponseEntity(HttpStatus.ACCEPTED);
            } else {
                return new ResponseEntity(HttpStatus.NOT_ACCEPTABLE);
            }
        } else {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }
    }

    //作为管理员获取一个活动的结构
    @ResponseBody
    @RequestMapping(value = "/admin/activity/{id}", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> getActivityStructure(@PathVariable long id, HttpSession session) {
        if (checkUser(session)) {
            return new ResponseEntity<>(adminService.getActivityStructure(id), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    //删除一个报名
    @ResponseBody
    @RequestMapping(value = "/admin/activity/{id}", method = RequestMethod.DELETE, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> deleteActivity(@PathVariable long id, HttpSession session) {
        if (checkUser(session)) {
            return new ResponseEntity<>(adminService.deleteActivity(id), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    //获取一个报名内的报名信息
    @ResponseBody
    @RequestMapping(value = "/admin/activity/{id}/applicant", method = RequestMethod.GET, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> getActivityApplicants(@PathVariable long id, HttpSession session) {
        if (checkUser(session)) {
            return new ResponseEntity<>(adminService.getActivityApplicants(id), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    //删除指定的报名信息
    @ResponseBody
    @RequestMapping(value = "/admin/activity/{aid}/applicant/{iid}", method = RequestMethod.DELETE, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> deleteApplicantInfo(@PathVariable long aid, @PathVariable int iid, HttpSession session) {
        if (checkUser(session)) {
            return new ResponseEntity<>(adminService.deleteApplicantInfo(aid, iid), HttpStatus.OK);
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    //更新报名信息
    @ResponseBody
    @RequestMapping(value = "/admin/activity/{id}", method = RequestMethod.PUT, produces = "application/json;charset=UTF-8")
    public ResponseEntity<String> alterActivityStructure(@PathVariable long id, HttpSession session, @RequestBody String jsonString) {
        if (checkUser(session)) {
            if (adminService.alterActivityStructure(id, new JSONObject(jsonString))) {
                return new ResponseEntity<>("", HttpStatus.OK);
            } else {
                return new ResponseEntity<>("", HttpStatus.NOT_ACCEPTABLE);
            }
        } else {
            return new ResponseEntity<>("", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * 修改当前申请状态
     * @param cspAuditStatusDTO the csp audit status dto
     * @param httpSession the http session
     * @return the response entity
     * @throws FailureException the failure exception
     */
    @PutMapping("/admin/csp/audit")
    public ResponseEntity<String> changeAuditStatus(@RequestBody CspAuditStatusDTO cspAuditStatusDTO, HttpSession httpSession)
        throws FailureException {
        if (checkUser(httpSession)) {
            String statusString = cspAuditStatusDTO.getStatus();
            if (!statusString.equals("STATUS_OPEN") && !statusString.equals("STATUS_CLOSED")) {
                throw new FailureException(HttpStatus.UNPROCESSABLE_ENTITY, "status字段错误");
            }
            cspService.changeAuditStatus(statusString);
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/admin/csp/audit/list/{pageNum}")
    public ResponseEntity<CspAuditListDTO> getAuditList(@PathVariable int pageNum, @RequestParam(required = false, defaultValue = "10") int pageSize,
        @RequestParam(required = false, defaultValue = "STATUS_UNCHECK") String status, HttpSession httpSession) throws FailureException {
        if (checkUser(httpSession)) {
            AuditStatus auditStatus;
            if (status.equals("ALL")) {
                auditStatus = null;
            } else {
                try {
                    auditStatus = AuditStatus.valueOf(status);
                } catch (Exception e) {
                    throw new FailureException(HttpStatus.UNPROCESSABLE_ENTITY, "status字段非法");
                }
            }
            return new ResponseEntity<>(cspService.getAuditList(pageNum, pageSize, auditStatus), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @PutMapping("/admin/csp/audit/{auditId}")
    public ResponseEntity<String> checkAudit(@PathVariable long auditId, @RequestBody CheckAuditDTO checkAuditDTO, HttpSession httpSession)
        throws FailureException {
        if (checkUser(httpSession)) {
            String comment = checkAuditDTO.getComment();
            AuditResult auditResult;
            try {
                auditResult = AuditResult.valueOf(checkAuditDTO.getResult());
            } catch (Exception e) {
                throw new FailureException(HttpStatus.UNPROCESSABLE_ENTITY, "result字段非法");
            }
            cspService.checkResult(auditId, auditResult, comment);
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping("/admin/csp/free")
    public ResponseEntity<CspFreeApplicantsDTO> getFreeApplicants(HttpSession httpSession, @RequestParam Timestamp start, @RequestParam Timestamp end)
        throws FailureException {
        if (checkUser(httpSession)) {

            return new ResponseEntity<>(cspService.getFreeApplicants(start, end), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/admin/csp/score")
    public ResponseEntity<String> uploadScore(HttpSession httpSession, @RequestBody CspScoreUploadDTO cspScoreUploadDTO) throws FailureException {
        if (checkUser(httpSession)) {
            cspService.uploadScore(cspScoreUploadDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    // 检查当前用户连接是否已经登陆
    private boolean checkUser(HttpSession session) {
        if (session == null) {
            return false;
        }
        String userName = session.getAttribute("username") == null ? null : session.getAttribute("username").toString();
        return userName != null && sessionList.get(userName).equals(session.getId());
    }

}
