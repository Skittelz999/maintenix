mock_provider "aws" {
  source = "./tests"
}

run "private_encrypted_demo_database" {
  command = apply

  assert {
    condition = (
      !aws_db_instance.postgres.publicly_accessible &&
      aws_db_instance.postgres.db_subnet_group_name == aws_db_subnet_group.postgres.name &&
      toset(aws_db_subnet_group.postgres.subnet_ids) == toset(values(output.private_subnet_ids)) &&
      toset(aws_db_instance.postgres.vpc_security_group_ids) == toset([aws_security_group.rds.id]) &&
      aws_db_instance.postgres.port == 5432
    )
    error_message = "PostgreSQL must use only private subnets and the existing restricted RDS security group."
  }

  assert {
    condition = (
      aws_db_instance.postgres.storage_encrypted &&
      aws_db_instance.postgres.manage_master_user_password &&
      aws_db_instance.postgres.password == null &&
      aws_db_instance.postgres.parameter_group_name == aws_db_parameter_group.postgres.name &&
      aws_db_parameter_group.postgres.family == "postgres17" &&
      anytrue([for parameter in aws_db_parameter_group.postgres.parameter : parameter.name == "rds.force_ssl" && parameter.value == "1"]) &&
      output.db_master_secret_arn == aws_db_instance.postgres.master_user_secret[0].secret_arn
    )
    error_message = "RDS must encrypt storage, require TLS and manage the password without exposing its value."
  }

  assert {
    condition = (
      aws_db_instance.postgres.engine == "postgres" &&
      aws_db_instance.postgres.engine_version == "17" &&
      aws_db_instance.postgres.instance_class == "db.t4g.micro" &&
      aws_db_instance.postgres.allocated_storage == 20 &&
      aws_db_instance.postgres.storage_type == "gp3" &&
      aws_db_instance.postgres.max_allocated_storage == 0 &&
      !aws_db_instance.postgres.multi_az &&
      !aws_db_instance.postgres.performance_insights_enabled &&
      aws_db_instance.postgres.monitoring_interval == 0 &&
      aws_db_instance.postgres.engine_lifecycle_support == "open-source-rds-extended-support-disabled" &&
      !aws_db_instance.postgres.deletion_protection &&
      aws_db_instance.postgres.skip_final_snapshot &&
      aws_db_instance.postgres.delete_automated_backups &&
      aws_db_instance.postgres.backup_retention_period == 1
    )
    error_message = "Demo defaults must remain small, single-AZ and removable without retained backups."
  }
}

run "custom_database_settings" {
  command = plan
  variables {
    db_name              = "demodb"
    db_username          = "demo_owner"
    db_instance_class    = "db.t4g.small"
    db_allocated_storage = 30
  }
  assert {
    condition = (
      aws_db_instance.postgres.db_name == "demodb" &&
      aws_db_instance.postgres.username == "demo_owner" &&
      aws_db_instance.postgres.instance_class == "db.t4g.small" &&
      aws_db_instance.postgres.allocated_storage == 30
    )
    error_message = "Database inputs must configure the instance."
  }
}

run "reject_small_storage" {
  command = plan
  variables {
    db_allocated_storage = 10
  }
  expect_failures = [var.db_allocated_storage]
}

run "reject_invalid_database_name" {
  command = plan
  variables {
    db_name = "invalid-name"
  }
  expect_failures = [var.db_name]
}

run "reject_reserved_username" {
  command = plan
  variables {
    db_username = "rdsadmin"
  }
  expect_failures = [var.db_username]
}
