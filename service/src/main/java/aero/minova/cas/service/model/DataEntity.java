package aero.minova.cas.service.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
@MappedSuperclass
public abstract class DataEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "KeyLong")
	private Long keyLong;

	@Column(name = "KeyText")
	private String keyText;

	@Column(name = "LastAction")
	private Integer lastAction = 1;

	@Size(max = 50)
	@Column(name = "LastUser")
	String lastUser = "CAS_JPA";

	@Column(name = "LastDate")
	LocalDateTime lastDate = LocalDateTime.now();

}