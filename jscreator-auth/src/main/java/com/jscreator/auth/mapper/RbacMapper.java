package com.jscreator.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * role / permission / roleandpermission_middle 的 SQL，逐条对齐 Express 版 modules/rbac/rbac.dao。
 * 参数用 Object 是因为原版把请求体里的值原样塞进 SQL（字符串 "3" 与数字 3 都能用）。
 */
public interface RbacMapper {

    @Select("SELECT * FROM role")
    List<LinkedHashMap<String, Object>> roleGetAll();

    @Insert("INSERT INTO role (role_name) VALUES (#{roleName})")
    int roleAdd(@Param("roleName") String roleName);

    @Delete("DELETE FROM role WHERE role_id = #{roleId}")
    int roleDelete(@Param("roleId") Object roleId);

    @Update("UPDATE role SET role_name = #{roleName} WHERE role_id = #{roleId}")
    int roleUpdateName(@Param("roleId") Object roleId, @Param("roleName") String roleName);

    @Select("SELECT permission_id FROM roleandpermission_middle WHERE role_id = #{roleId}")
    List<Object> rolePermissionIds(@Param("roleId") Object roleId);

    @Delete("DELETE FROM roleandpermission_middle WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") Object roleId);

    @Insert("INSERT INTO roleandpermission_middle (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insertRolePermission(@Param("roleId") Object roleId, @Param("permissionId") Object permissionId);

    @Select("SELECT * FROM permission")
    List<LinkedHashMap<String, Object>> permissionGetAll();

    @Update("UPDATE permission SET permission_name = #{permissionName}, permission_description = #{permissionDescription} "
            + "WHERE permission_id = #{permissionId}")
    int permissionUpdate(@Param("permissionId") Object permissionId,
                         @Param("permissionName") String permissionName,
                         @Param("permissionDescription") Object permissionDescription);
}
