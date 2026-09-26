mock_resource "aws_db_instance" {
  defaults = {
    master_user_secret = [{
      secret_arn    = "arn:aws:secretsmanager:eu-north-1:123456789012:secret:test-placeholder"
      kms_key_id    = "test-key"
      secret_status = "active"
    }]
  }
}
