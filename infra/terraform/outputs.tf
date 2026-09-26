output "vpc_id" {
  description = "VPC for the future application infrastructure."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs keyed by AZ; for the future ALB and Fargate tasks with assign_public_ip = true."
  value       = { for az, subnet in aws_subnet.public : az => subnet.id }
}

output "private_subnet_ids" {
  description = "Private subnet IDs keyed by AZ; used by the non-public RDS database."
  value       = { for az, subnet in aws_subnet.private : az => subnet.id }
}

output "security_group_ids" {
  description = "Security groups for the future ALB/ECS resources and the RDS database."
  value = {
    alb = aws_security_group.alb.id
    ecs = aws_security_group.ecs.id
    rds = aws_security_group.rds.id
  }
}

output "db_identifier" {
  description = "RDS instance identifier for status checks."
  value       = aws_db_instance.postgres.identifier
}

output "db_address" {
  description = "Private PostgreSQL DNS hostname; use this hostname for TLS verification."
  value       = aws_db_instance.postgres.address
}

output "db_port" {
  description = "PostgreSQL connection port."
  value       = aws_db_instance.postgres.port
}

output "db_name" {
  description = "Initial PostgreSQL database name."
  value       = aws_db_instance.postgres.db_name
}

output "db_master_secret_arn" {
  description = "ARN only of the RDS-managed master credentials; no secret value is read by Terraform."
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
}
