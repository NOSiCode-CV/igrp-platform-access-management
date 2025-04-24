package cv.igrp.platform.access_management.users.application.commands.commands;

import java.util.List;

import cv.igrp.framework.core.domain.Command;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveRolesFromUserCommand implements Command {
  private Integer id; // id do usuário
  private List<Integer> roleIds; // lista de role IDs a remover

  // Getters e Setters
  public Integer getId() {
      return id;
  }

  public void setId(Integer id) {
      this.id = id;
  }

  public List<Integer> getRoleIds() {
      return roleIds;
  }

  public void setRoleIds(List<Integer> roleIds) {
      this.roleIds = roleIds;
  }
}
