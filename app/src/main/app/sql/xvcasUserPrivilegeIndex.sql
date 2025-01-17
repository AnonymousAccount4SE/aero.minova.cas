alter view dbo.xvcasUserPrivilegeIndex
with encryption as
	select	distinct
			up.KeyLong,
			up.KeyText,
			up.Description,
			ug.KeyLong as UserGroupKey,
			lu.RowLevelSecurity
	from xtcasUserPrivilege up
	left join xtcasLuUserPrivilegeUserGroup lu on lu.UserPrivilegeKey = up.KeyLong
	left join xtcasUserGroup ug on  ug.KeyLong=lu.UserGroupKey
	where up.LastAction > 0
	and ug.LastAction > 0