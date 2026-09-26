resource "aws_db_subnet_group" "postgres" {
  name       = "${local.name}-postgres"
  subnet_ids = [for subnet in aws_subnet.private : subnet.id]

  tags = { Name = "${local.name}-postgres" }
}

resource "aws_db_parameter_group" "postgres" {
  name_prefix = "${local.name}-postgres-"
  family      = "postgres17"
  description = "Require TLS for Maintenix PostgreSQL connections"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "postgres" {
  identifier                  = "${local.name}-postgres"
  engine                      = "postgres"
  engine_version              = "17"
  auto_minor_version_upgrade  = true
  engine_lifecycle_support    = "open-source-rds-extended-support-disabled"
  instance_class              = var.db_instance_class
  db_name                     = var.db_name
  username                    = var.db_username
  manage_master_user_password = true

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = 0
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  parameter_group_name   = aws_db_parameter_group.postgres.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  port                   = 5432
  multi_az               = false

  backup_retention_period      = 1
  performance_insights_enabled = false
  monitoring_interval          = 0
  deletion_protection          = false
  skip_final_snapshot          = true
  delete_automated_backups     = true

  tags = { Name = "${local.name}-postgres" }
}
